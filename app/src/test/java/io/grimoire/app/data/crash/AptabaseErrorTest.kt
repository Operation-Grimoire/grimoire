package io.grimoire.app.data.crash

import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AptabaseErrorTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun report(now: Instant = Instant.now(), debug: Boolean = false) = buildAptabaseError(
        IllegalStateException("private novel https://site.test/token?key=secret /storage/user/book"),
        "1.0.1 (abcdef12)", "15", "temporary-session", debug, now,
    )

    @Test fun automaticPayloadContainsTypesAndFramesButNoMessagesPathsOrBreadcrumbs() {
        val error = IllegalStateException("private-title https://site/token?password=abc", IOExceptionForTest("/private/path"))
        error.stackTrace = arrayOf(StackTraceElement("io.grimoire.Reader", "load", "/private/novel-title.kt", 42))
        error.cause!!.stackTrace = arrayOf(StackTraceElement("io.grimoire.Source", "fetch", "https://private", 7))
        val value = buildAptabaseError(error, "1.0", "15", "session", true)
        val body = Json.encodeToString(value)
        assertEquals("java.lang.IllegalStateException", value.errorMessage)
        assertTrue(value.stackTrace.contains("io.grimoire.Reader.load:42"))
        assertTrue(value.stackTrace.contains("Caused by:"))
        for (secret in listOf("private-title", "https://", "password", "/private", "novel-title")) {
            assertFalse(body, body.contains(secret))
        }
        assertTrue(value.isDebug)
        assertEquals("fatal", value.severity)
        assertEquals("crash", value.kind)
    }

    @Test fun oversizedAndCyclicCausesStayBounded() {
        val first = RuntimeException("x".repeat(100_000))
        val second = RuntimeException("secret", first)
        first.initCause(second)
        first.stackTrace = Array(1_000) { StackTraceElement("a".repeat(500), "b".repeat(500), "private-file", it) }
        val value = buildAptabaseError(first, "v".repeat(100), "o".repeat(150), "s".repeat(200), false)
        assertTrue(value.stackTrace.length <= 10_000)
        assertEquals(50, value.appVersion.length)
        assertEquals(100, value.osVersion.length)
        assertEquals(100, value.sessionId.length)
        assertFalse(value.stackTrace.contains("secret"))
    }

    @Test fun fatalCaptureSurvivesRelaunchAndPreservesOriginalBuildAndTimestamp() {
        val folder = temporary.newFolder()
        val original = report(debug = true)
        val capture = AptabaseCrashCapture(AptabaseErrorQueue(folder), configured = true)
        capture.capture { fail("Must await persisted consent"); original }
        capture.setEnabled(true)
        capture.capture { original }
        capture.capture { fail("Duplicate handler invocation"); original }
        assertEquals(listOf(original), AptabaseErrorQueue(folder).pending().map { it.second })
        assertTrue(AptabaseErrorQueue(folder).pending().single().second.isDebug)
    }

    @Test fun noKeyAndOptOutPreventCapture() {
        for (configured in listOf(false, true)) {
            val queue = AptabaseErrorQueue(temporary.newFolder())
            val capture = AptabaseCrashCapture(queue, configured)
            capture.setEnabled(!configured)
            capture.capture { fail("Disabled/unconfigured capture must not build a payload"); report() }
            assertTrue(queue.pending().isEmpty())
        }
    }

    @Test fun immediateOptOutClearsPreviouslyCapturedReportsBeforeAnyAsyncPreferenceWrite() {
        val folder = temporary.newFolder()
        val capture = AptabaseCrashCapture(AptabaseErrorQueue(folder), configured = true)
        capture.setEnabled(true)
        capture.capture { report() }
        assertEquals(1, AptabaseErrorQueue(folder).pending().size)
        capture.disableAndClear()
        capture.capture { fail("Capture must stay disabled"); report() }
        // Simulate process death/relaunch without the DataStore write or collector.
        assertTrue(AptabaseErrorQueue(folder).pending().isEmpty())
    }

    @Test fun queueIsBoundedAndExpiresOldOrDamagedRecords() {
        val folder = temporary.newFolder()
        val queue = AptabaseErrorQueue(folder)
        repeat(12) { queue.add(report().copy(sessionId = "session-$it")) }
        assertEquals(AptabaseErrorQueue.MAX_REPORTS, queue.pending().size)
        assertEquals(AptabaseErrorQueue.MAX_REPORTS, folder.listFiles()!!.size)
        File(folder, "incomplete.tmp").writeText("partial")
        File(folder, "corrupt.json").writeText("not-json")
        File(folder, "oversized.json").writeText("x".repeat(70_000))
        assertEquals(AptabaseErrorQueue.MAX_REPORTS, queue.pending().size)
        assertFalse(File(folder, "incomplete.tmp").exists())
        assertFalse(File(folder, "corrupt.json").exists())
        assertFalse(File(folder, "oversized.json").exists())
        assertTrue(queue.pending(Instant.now().plusSeconds(15 * 24 * 3600)).isEmpty())
        assertTrue(folder.listFiles()!!.isEmpty())
    }

    @Test fun retrySurvivesRelaunchAndAcceptedOrPermanentFailuresAreRemoved() = runBlocking {
        val folder = temporary.newFolder()
        var queue = AptabaseErrorQueue(folder)
        queue.add(report())
        assertTrue(drainAptabaseErrors(queue, { true }, { ErrorDelivery.RETRY }))
        queue = AptabaseErrorQueue(folder)
        assertEquals(1, queue.pending().size)
        assertFalse(drainAptabaseErrors(queue, { true }, { ErrorDelivery.ACCEPTED }))
        assertTrue(queue.pending().isEmpty())
        queue.add(report())
        assertFalse(drainAptabaseErrors(queue, { true }, { ErrorDelivery.DROP }))
        assertTrue(queue.pending().isEmpty())
    }

    @Test fun optOutClearsQueueAndIsRecheckedBeforeEveryRequest() = runBlocking {
        val queue = AptabaseErrorQueue(temporary.newFolder())
        repeat(3) { queue.add(report()) }
        var allowed = true
        var sends = 0
        assertFalse(drainAptabaseErrors(queue, { allowed }) {
            sends++
            allowed = false
            ErrorDelivery.ACCEPTED
        })
        assertEquals(1, sends)
        assertTrue(queue.pending().isEmpty())
        queue.add(report())
        assertFalse(drainAptabaseErrors(queue, { false }) { fail("Must not send"); ErrorDelivery.ACCEPTED })
        assertTrue(queue.pending().isEmpty())
    }

    @Test fun cancellationRetainsInFlightReportForNextLaunch() = runBlocking {
        val queue = AptabaseErrorQueue(temporary.newFolder())
        queue.add(report())
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            drainAptabaseErrors(queue, { true }) { awaitCancellation() }
        }
        job.cancelAndJoin()
        assertEquals(1, queue.pending().size)
    }

    @Test fun hostedEndpointUsesKeyRegionAndInvalidKeysAreInert() {
        assertEquals("https://eu.aptabase.com/api/v0/error", aptabaseErrorEndpoint("A-EU-123"))
        assertEquals("https://us.aptabase.com/api/v0/error", aptabaseErrorEndpoint(" a-us-456 "))
        for (key in listOf("", "A-SH-123", "A-DEV-123", "A-US-", "A-US-123-other", "A-XX-123")) {
            assertNull(key, aptabaseErrorEndpoint(key))
        }
    }

    @Test fun apiQuotaAndPermanentFailuresAreDroppedWhileTransientFailuresRetry() {
        assertEquals(ErrorDelivery.ACCEPTED, errorDelivery(202))
        for (status in listOf(400, 401, 403, 404, 302)) assertEquals(ErrorDelivery.DROP, errorDelivery(status))
        for (status in listOf(408, 429, 500, 502, 503)) assertEquals(ErrorDelivery.RETRY, errorDelivery(status))
    }

    @Test fun realHttpRequestMatchesContractDoesNotFollowRedirectsAndRetriesConnectionFailure() = runBlocking {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = AtomicInteger()
        val status = AtomicInteger(202)
        var receivedKey = ""
        var receivedBody = ""
        var receivedMethod = ""
        server.createContext("/api/v0/error") { exchange ->
            receivedKey = exchange.requestHeaders.getFirst("App-Key")
            receivedMethod = exchange.requestMethod
            receivedBody = exchange.requestBody.bufferedReader().use { it.readText() }
            requests.incrementAndGet()
            if (status.get() == 302) exchange.responseHeaders.add("Location", "/redirect-target")
            exchange.sendResponseHeaders(status.get(), -1)
            exchange.close()
        }
        server.createContext("/redirect-target") { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(202, -1)
            exchange.close()
        }
        server.start()
        val transport = AptabaseErrorTransport("A-EU-test", "http://127.0.0.1:${server.address.port}/api/v0/error")
        try {
            val original = report(debug = true)
            assertEquals(ErrorDelivery.ACCEPTED, transport.send(original))
            assertEquals("POST", receivedMethod)
            assertEquals("A-EU-test", receivedKey)
            val body = Json.parseToJsonElement(receivedBody).jsonObject
            assertEquals(original.timestamp, body.getValue("timestamp").jsonPrimitive.content)
            assertEquals("true", body.getValue("isDebug").jsonPrimitive.content)
            assertEquals("fatal", body.getValue("severity").jsonPrimitive.content)
            assertEquals("crash", body.getValue("kind").jsonPrimitive.content)
            assertFalse(receivedBody.contains("private novel"))
            status.set(403)
            assertEquals(ErrorDelivery.DROP, transport.send(original))
            status.set(429)
            assertEquals(ErrorDelivery.RETRY, transport.send(original))
            status.set(302)
            assertEquals(ErrorDelivery.DROP, transport.send(original))
            assertEquals(4, requests.get())
        } finally {
            server.stop(0)
        }
        assertEquals(ErrorDelivery.RETRY, transport.send(report()))
    }

    private class IOExceptionForTest(message: String) : RuntimeException(message)
}
