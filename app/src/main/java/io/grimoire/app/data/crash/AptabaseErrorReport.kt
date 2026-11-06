package io.grimoire.app.data.crash

import java.time.Instant
import java.util.Collections
import java.util.IdentityHashMap
import kotlinx.serialization.Serializable

/** The documented /api/v0/error body. Never serialize Throwable messages or local crash logs. */
@Serializable
internal data class AptabaseErrorReport(
    val errorMessage: String,
    val errorType: String,
    val stackTrace: String,
    val timestamp: String,
    val appVersion: String,
    val osVersion: String,
    val sessionId: String,
    val isDebug: Boolean,
    val platform: String = "Android",
    val osName: String = "Android",
    val sdkVersion: String = "grimoire-aptabase-errors@1",
    val severity: String = "fatal",
    val kind: String = "crash",
)

internal fun buildAptabaseError(
    throwable: Throwable,
    appVersion: String,
    osVersion: String,
    sessionId: String,
    isDebug: Boolean,
    now: Instant = Instant.now(),
): AptabaseErrorReport {
    // An allowlist, rather than message regexes: messages often contain novel
    // titles, credentials, URLs or filesystem paths. Keep only JVM identifiers
    // and line numbers, including cause types, for grouping and diagnosis.
    fun identifier(value: String) = value.take(200).replace(Regex("[^A-Za-z0-9_.$<>]"), "_")
    val seen = Collections.newSetFromMap(IdentityHashMap<Throwable, Boolean>())
    val stack = buildString {
        var current: Throwable? = throwable
        while (current != null && seen.size < 8 && seen.add(current)) {
            if (isNotEmpty()) append("Caused by: ")
            appendLine(identifier(current.javaClass.name))
            current.stackTrace.take(64).forEach { frame ->
                append("  at ").append(identifier(frame.className)).append('.')
                    .append(identifier(frame.methodName)).append(':')
                    .appendLine(frame.lineNumber.coerceAtLeast(0))
            }
            current = current.cause
        }
    }.take(10_000)
    val type = identifier(throwable.javaClass.name).take(100)
    return AptabaseErrorReport(
        errorMessage = type,
        errorType = type,
        stackTrace = stack,
        timestamp = now.toString(),
        appVersion = appVersion.take(50),
        osVersion = osVersion.take(100),
        sessionId = sessionId.take(100),
        isDebug = isDebug,
    )
}
