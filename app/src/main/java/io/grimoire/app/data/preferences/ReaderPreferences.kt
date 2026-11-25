package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

typealias ReaderFont = io.grimoire.ui.reader.ReaderFont
typealias ReaderColorTheme = io.grimoire.ui.reader.ReaderColorTheme
typealias ReaderOrientation = io.grimoire.ui.reader.ReaderOrientation
typealias MarkAsReadStrategy = io.grimoire.ui.reader.MarkAsReadStrategy

@Singleton
class ReaderPreferences @Inject constructor(store: PreferenceStore) {
    val markAsReadStrategy = store.getEnum("reader_mark_as_read_strategy", MarkAsReadStrategy.PARAGRAPHS_FROM_END)
    val markAsReadThreshold = store.getInt("reader_mark_as_read_threshold", 95)
    val markAsReadParagraphsFromEnd = store.getInt("reader_mark_as_read_paragraphs_from_end", 3)
    val fontSize = store.getInt("reader_font_size", 16)
    val lineHeightTimes10 = store.getInt("reader_line_height_x10", 16) // 1.6× stored as 16
    val paragraphSpacing = store.getInt("reader_paragraph_spacing", 16)
    val readerFont = store.getEnum("reader_font", ReaderFont.DEFAULT)
    val colorTheme = store.getObject<ReaderColorTheme?>(
        "reader_color_theme",
        defaultValue = null,
        serialize = { it?.name.orEmpty() },
        deserialize = { raw -> ReaderColorTheme.entries.firstOrNull { it.name == raw } },
    )
    val orientation = store.getEnum("reader_orientation", ReaderOrientation.FREE)
    val hideNotificationBar = store.getBoolean("reader_hide_notification_bar", false)
    val hideInlineImages = store.getBoolean("reader_hide_inline_images", false)
    val showChapterProgressPercent = store.getBoolean("reader_show_chapter_progress_percent", true)
    val showNovelProgressPercent = store.getBoolean("reader_show_novel_progress_percent", true)
    val grimoireEasterEggEnabled = store.getBoolean("reader_grimoire_easter_egg_enabled", true)
}

internal fun resolveReaderColorTheme(saved: ReaderColorTheme?, systemDark: Boolean): ReaderColorTheme =
    io.grimoire.ui.reader.resolveReaderColorTheme(saved, systemDark)
