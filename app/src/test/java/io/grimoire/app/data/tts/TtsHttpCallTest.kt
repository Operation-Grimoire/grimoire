package io.grimoire.app.data.tts

import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class TtsHttpCallTest {
    @Test fun cancellationCancelsTransportAndClosesLateResponse() = runBlocking {
        val call = FakeCall()
        var consumed = false
        val job = launch { call.awaitTtsResult { consumed = true } }
        yield()
        job.cancel()
        job.join()
        assertTrue(call.isCanceled())
        val body = TrackingBody()
        call.reply(body)
        assertTrue(body.closed)
        assertFalse(consumed)
    }

    @Test fun successfulResponseIsConsumedAndClosed() = runBlocking {
        val call = FakeCall()
        var result: String? = null
        val job = launch { result = call.awaitTtsResult { it.body!!.string() } }
        yield()
        val body = TrackingBody()
        call.reply(body)
        job.join()
        assertEquals("audio", result)
        assertTrue(body.closed)
        assertFalse(call.isCanceled())
    }

    @Test fun transportCancellationRemainsConnectedDuringBodyConsumption() = runBlocking {
        val call = FakeCall()
        lateinit var job: Job
        job = launch {
            call.awaitTtsResult {
                job.cancel()
                assertTrue(call.isCanceled())
                it.body!!.string()
            }
        }
        yield()
        val body = TrackingBody()
        call.reply(body)
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(body.closed)
    }

    private class TrackingBody : ResponseBody() {
        var closed = false
        private val data = object : ForwardingSource(Buffer().writeUtf8("audio")) {
            override fun close() { closed = true; super.close() }
        }.buffer()
        override fun contentType(): MediaType? = null
        override fun contentLength() = 5L
        override fun source(): BufferedSource = data
    }

    private class FakeCall : Call {
        private val request = Request.Builder().url("https://example.invalid").build()
        private lateinit var callback: Callback
        private var canceled = false
        private var executed = false
        override fun request() = request
        override fun execute(): Response = error("Use enqueue")
        override fun enqueue(responseCallback: Callback) { executed = true; callback = responseCallback }
        override fun cancel() { canceled = true }
        override fun isExecuted() = executed
        override fun isCanceled() = canceled
        override fun timeout() = Timeout()
        override fun clone(): Call = FakeCall()
        fun reply(body: ResponseBody) {
            callback.onResponse(this, Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body).build())
        }
    }
}
