package io.grimoire.app.ui.screen.tasks

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.grimoire.app.R

/** Localizes the compact summaries persisted by background download and sync workers. */
@Composable
internal fun localizedTaskSummary(summary: String): String {
    DOWNLOAD_SUMMARY.matchEntire(summary)?.let { match ->
        val downloaded = match.groupValues[1].toInt()
        val failed = match.groupValues[2].toIntOrNull()
        return listOfNotNull(
            pluralStringResource(
                R.plurals.tasks_downloaded_summary,
                downloaded,
                downloaded,
            ),
            failed?.let {
                pluralStringResource(R.plurals.tasks_failed_count, it, it)
            },
        ).joinToString(" · ")
    }
    if (summary.startsWith("Library sync failed · ")) {
        return stringResource(
            R.string.tasks_sync_failed,
            summary.removePrefix("Library sync failed · "),
        )
    }
    SYNC_SUMMARY.matchEntire(summary)?.let { match ->
        val checked = match.groupValues[1].toInt()
        val parts = match.groupValues[2].split(", ").map { part ->
            val value = part.substringBefore(' ').toIntOrNull() ?: return@map part
            when {
                "new chapter" in part -> pluralStringResource(
                    R.plurals.tasks_new_chapters,
                    value,
                    value,
                )
                "warning" in part -> pluralStringResource(
                    R.plurals.tasks_warning_count,
                    value,
                    value,
                )
                "error" in part -> pluralStringResource(
                    R.plurals.tasks_error_count,
                    value,
                    value,
                )
                else -> part
            }
        }
        return listOf(
            pluralStringResource(R.plurals.tasks_checked_novels, checked, checked),
            parts.joinToString(", "),
        ).joinToString(" · ")
    }
    return summary
}

private val DOWNLOAD_SUMMARY = Regex("""(\d+) chapters? downloaded(?: · (\d+) failed)?""")
private val SYNC_SUMMARY = Regex("""Checked (\d+) novels? · (.+)""")
