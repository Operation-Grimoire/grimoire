package io.grimoire.app.data.preferences

import javax.inject.Inject
import javax.inject.Singleton

enum class ReaderFont { DEFAULT, SERIF, MONOSPACE }
enum class ReaderColorTheme { LIGHT, SEPIA, DARK, BLACK }
enum class ReaderOrientation { FREE, PORTRAIT, LANDSCAPE }

@Singleton
class ReaderPreferences @Inject constructor(store: PreferenceStore) {
    val markAsReadThreshold = store.getInt("reader_mark_as_read_threshold", 85)
    val fontSize = store.getInt("reader_font_size", 16)
    val lineHeightTimes10 = store.getInt("reader_line_height_x10", 16) // 1.6× stored as 16
    val paragraphSpacing = store.getInt("reader_paragraph_spacing", 16)
    val readerFont = store.getEnum("reader_font", ReaderFont.DEFAULT)
    val colorTheme = store.getEnum("reader_color_theme", ReaderColorTheme.LIGHT)
    val orientation = store.getEnum("reader_orientation", ReaderOrientation.FREE)
    val hideNotificationBar = store.getBoolean("reader_hide_notification_bar", false)
    val hideInlineImages = store.getBoolean("reader_hide_inline_images", false)
    val showChapterProgressPercent = store.getBoolean("reader_show_chapter_progress_percent", true)
    val showNovelProgressPercent = store.getBoolean("reader_show_novel_progress_percent", true)
}
