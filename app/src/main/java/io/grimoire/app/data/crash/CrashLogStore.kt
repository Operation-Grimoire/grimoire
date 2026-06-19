package io.grimoire.app.data.crash

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.app.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists the most recent uncaught-exception report to a flat file so it
 * survives the process death that follows a crash and can be surfaced on the
 * next launch.
 *
 * One file, overwritten per crash; the *presence* of the file is the "there is
 * a crash the user hasn't dismissed yet" flag — no separate preference needed.
 * [clear] (called when the user dismisses the crash screen) deletes it, and the
 * crash screen stops appearing on subsequent launches.
 */
@Singleton
class CrashLogStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val file: File
        get() = File(context.filesDir, FILE_NAME)

    /**
     * Writes a crash report synchronously. Called from the uncaught-exception
     * handler on the crashing thread with the process about to die, so it does
     * its IO inline (no coroutine) and swallows any secondary failure — a
     * failed write must never mask the original crash.
     */
    fun save(throwable: Throwable, thread: Thread) {
        runCatching { file.writeText(buildReport(throwable, thread)) }
    }

    fun hasPendingCrash(): Boolean = file.exists()

    fun readPending(): String? = runCatching {
        file.takeIf { it.exists() }?.readText()
    }.getOrNull()

    fun clear() {
        runCatching { file.delete() }
    }

    private fun buildReport(throwable: Throwable, thread: Thread): String {
        val stack = StringWriter()
            .also { throwable.printStackTrace(PrintWriter(it)) }
            .toString()
            .trimEnd()
        val summary = "${throwable.javaClass.name}: ${throwable.message ?: "(no message)"}"
        val packageInfo = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0)
        }.getOrNull()
        val versionName = packageInfo?.versionName ?: "?"
        val versionCode = packageInfo?.let { PackageInfoCompat.getLongVersionCode(it) } ?: -1L
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        return buildString {
            // First line is the exception summary so the crash screen, the copy
            // action, and the GitHub issue title all read off the same source.
            appendLine(summary)
            appendLine("==============================")
            appendLine("Time:    $time")
            appendLine("App:     $versionName ($versionCode)")
            appendLine("Commit:  ${BuildConfig.GIT_SHA.ifBlank { "(unknown)" }}")
            appendLine("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
            appendLine("Device:  ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread:  ${thread.name}")
            appendLine()
            append(stack)
        }
    }

    companion object {
        private const val FILE_NAME = "last_crash.txt"
    }
}

private const val CRASH_REPO = "Operation-Grimoire/grimoire"

// GitHub serves the prefilled "new issue" page over GET, so the whole report
// rides in the URL. Browsers and the server cap URL length, so cap the body and
// point users at the (full) copied log for the overflow.
private const val MAX_URL_BODY = 6000

/**
 * Builds a prefilled GitHub "new issue" URL from a [report] produced by
 * [CrashLogStore]. The first line of the report becomes the issue title; the
 * body wraps the (possibly truncated) report in a collapsible code block and
 * leaves room for the user to describe what they were doing.
 */
fun buildCrashIssueUrl(report: String): String {
    val title = report.lineSequence().firstOrNull()?.take(120)?.ifBlank { null } ?: "App crash"
    val log = if (report.length > MAX_URL_BODY) {
        report.take(MAX_URL_BODY) + "\n…(truncated — use \"Copy details\" for the full log)"
    } else {
        report
    }
    val body = buildString {
        appendLine("**What were you doing when the app crashed?**")
        appendLine()
        appendLine("_(describe here)_")
        appendLine()
        appendLine("<details><summary>Crash log</summary>")
        appendLine()
        appendLine("```")
        appendLine(log)
        appendLine("```")
        appendLine()
        appendLine("</details>")
    }
    return "https://github.com/$CRASH_REPO/issues/new" +
        "?title=" + Uri.encode("Crash: $title") +
        "&labels=crash" +
        "&body=" + Uri.encode(body)
}
