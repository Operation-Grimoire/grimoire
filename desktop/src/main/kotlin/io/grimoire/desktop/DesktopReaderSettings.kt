package io.grimoire.desktop

import io.grimoire.api.model.novel.Chapter
import io.grimoire.ui.reader.ReaderColorTheme
import io.grimoire.ui.reader.ReaderFont
import io.grimoire.ui.reader.ReaderTextAlign
import org.json.JSONObject

/** Reader display choices; same ranges and defaults as the Android reader. */
data class DesktopReaderSettings(
    val fontSize: Int = 16,
    val lineHeightTimes10: Int = 16,
    val paragraphSpacing: Int = 16,
    val font: ReaderFont = ReaderFont.DEFAULT,
    /** Null follows the app's light or dark theme. */
    val colorTheme: ReaderColorTheme? = null,
    val textAlign: ReaderTextAlign = ReaderTextAlign.AUTO,
    val showChapterPercent: Boolean = true,
    val showBookPercent: Boolean = true,
    val grimoireEasterEgg: Boolean = true,
) {
    /** Clamps to the ranges the settings sheet offers. */
    fun normalized() = copy(
        fontSize = fontSize.coerceIn(12, 32),
        lineHeightTimes10 = lineHeightTimes10.coerceIn(10, 30),
        paragraphSpacing = paragraphSpacing.coerceIn(0, 32),
    )
}

internal fun DesktopReaderSettings.encode(): String = JSONObject()
    .put("fontSize", fontSize).put("lineHeightTimes10", lineHeightTimes10).put("paragraphSpacing", paragraphSpacing)
    .put("font", font.name).put("colorTheme", colorTheme?.name ?: "").put("textAlign", textAlign.name)
    .put("showChapterPercent", showChapterPercent).put("showBookPercent", showBookPercent)
    .put("grimoireEasterEgg", grimoireEasterEgg).toString()

/** Missing, unknown or malformed values fall back to their defaults one by one. */
internal fun decodeReaderSettings(raw: String?): DesktopReaderSettings {
    val defaults = DesktopReaderSettings()
    val json = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return defaults
    fun <T : Enum<T>> enum(key: String, entries: List<T>, fallback: T?) = json.optString(key).let { name -> entries.firstOrNull { it.name == name } } ?: fallback
    return DesktopReaderSettings(
        fontSize = json.optInt("fontSize", defaults.fontSize),
        lineHeightTimes10 = json.optInt("lineHeightTimes10", defaults.lineHeightTimes10),
        paragraphSpacing = json.optInt("paragraphSpacing", defaults.paragraphSpacing),
        font = enum("font", ReaderFont.entries, defaults.font)!!,
        colorTheme = enum("colorTheme", ReaderColorTheme.entries, null),
        textAlign = enum("textAlign", ReaderTextAlign.entries, defaults.textAlign)!!,
        showChapterPercent = json.optBoolean("showChapterPercent", defaults.showChapterPercent),
        showBookPercent = json.optBoolean("showBookPercent", defaults.showBookPercent),
        grimoireEasterEgg = json.optBoolean("grimoireEasterEgg", defaults.grimoireEasterEgg),
    ).normalized()
}

/** Chapters in reading order: ascending [Chapter.chapterNumber] when all are numbered, or reversed when newest-first. */
internal fun readingOrder(chapters: List<Chapter>): List<Chapter> {
    if (chapters.size < 2) return chapters
    if (chapters.all { it.chapterNumber >= 0f }) return chapters.sortedBy { it.chapterNumber }
    val firstNumbered = chapters.firstOrNull { it.chapterNumber >= 0f } ?: return chapters
    val lastNumbered = chapters.last { it.chapterNumber >= 0f }
    return if (firstNumbered.chapterNumber > lastNumbered.chapterNumber) chapters.asReversed() else chapters
}

/** The nearest readable chapter before ([step] = -1) or after ([step] = 1) [current] in reading order. */
internal fun adjacentChapter(chapters: List<Chapter>, current: Chapter, step: Int): Chapter? {
    val ordered = readingOrder(chapters)
    val index = ordered.indexOfFirst { it.url == current.url }
    if (index < 0) return null
    return generateSequence(index + step) { it + step }
        .takeWhile { it in ordered.indices }
        .map { ordered[it] }
        .firstOrNull { !it.locked }
}
