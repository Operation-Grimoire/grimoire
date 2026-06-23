package io.grimoire.app.data.crash

import android.os.Debug
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/**
 * Process-global crash breadcrumb trail and runtime snapshots, read by
 * [CrashLogStore] when building a report.
 *
 * Deliberately a plain `object`, not a Hilt singleton: the uncaught-exception
 * handler must be able to read it with no dependency graph, on whatever thread
 * crashed, even very early in startup. Recording a breadcrumb is a cheap append
 * to a small ring buffer, so it is safe to call from hot paths (navigation,
 * background workers).
 *
 * The whole point is to answer "what was the app *doing* when it died?" — for an
 * OutOfMemoryError the crashing stack frame is just whoever happened to allocate
 * last and tells you nothing about the cause, so the breadcrumbs ([drop]) plus
 * the live heap figures ([memorySummary]) are what actually localize the bug.
 */
object CrashContext {

    private const val MAX_BREADCRUMBS = 25

    /** Most recent UI location, also surfaced as a dedicated report line. */
    @Volatile
    var currentRoute: String? = null
        private set

    private val lock = Any()
    private val breadcrumbs = ArrayDeque<String>(MAX_BREADCRUMBS)

    // Crumbs are recorded during normal operation (not during the crash), so the
    // per-crumb formatting cost is irrelevant; the crash path only reads.
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    /** Records a navigation change as both the current route and a breadcrumb. */
    fun setRoute(route: String?) {
        currentRoute = route
        drop("nav → ${route ?: "(root)"}")
    }

    /**
     * Records a one-line breadcrumb ("downloaded chapter X", "library sync
     * started", …). Keep it short and free of user content that shouldn't end up
     * in a shared crash report.
     */
    fun drop(message: String) {
        val line = "${timeFormat.format(Date())}  $message"
        synchronized(lock) {
            if (breadcrumbs.size >= MAX_BREADCRUMBS) breadcrumbs.pollFirst()
            breadcrumbs.addLast(line)
        }
    }

    /** Oldest-to-newest snapshot of the breadcrumb trail. */
    fun breadcrumbs(): List<String> = synchronized(lock) { breadcrumbs.toList() }

    /**
     * Live memory figures in MB: Java heap used/max and native heap allocated.
     * Reads only longs, so it is safe to call from inside an OOM handler. A Java
     * "used ≈ max" reading is the signature of a heap-exhaustion OOM.
     */
    fun memorySummary(): String {
        val rt = Runtime.getRuntime()
        val max = rt.maxMemory()
        val used = rt.totalMemory() - rt.freeMemory()
        val native = runCatching { Debug.getNativeHeapAllocatedSize() }.getOrDefault(-1L)
        fun mb(bytes: Long): String = if (bytes < 0) "?" else "${bytes / (1024 * 1024)}MB"
        return "Java ${mb(used)} used / ${mb(max)} max · native ${mb(native)}"
    }
}
