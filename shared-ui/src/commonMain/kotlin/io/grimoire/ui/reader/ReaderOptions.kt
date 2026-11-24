package io.grimoire.ui.reader

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection

// Persisted by name (or ordinal for ReaderTextAlign) on both platforms; append only.

enum class ReaderFont { DEFAULT, SERIF, MONOSPACE }
enum class ReaderColorTheme { LIGHT, SEPIA, DARK, BLACK }
enum class ReaderOrientation { FREE, PORTRAIT, LANDSCAPE }

/**
 * How the reader decides a chapter is "done" and auto-flips its `read` flag.
 *
 * Percentage doesn't scale well: on long chapters it requires scrolling past 85–95% of
 * paragraphs before mark-as-read fires, even though the user may have finished the meaningful
 * content and stopped at author-note / Q&A tail sections. The paragraph-from-end strategies
 * are length-agnostic.
 */
enum class MarkAsReadStrategy { PERCENT, PARAGRAPHS_FROM_END, AT_END }

/**
 * Per-novel reader text alignment. [AUTO] (the default) derives direction + alignment from the
 * novel's language — RTL scripts read right-aligned, everything else left. The rest are explicit
 * user overrides, remembered per novel.
 *
 * Append-only: Android stores the ordinal.
 */
enum class ReaderTextAlign { AUTO, LEFT, RIGHT, CENTER, JUSTIFY }

/** No saved choice follows the app's light/dark state. */
fun resolveReaderColorTheme(saved: ReaderColorTheme?, systemDark: Boolean): ReaderColorTheme =
    saved ?: if (systemDark) ReaderColorTheme.DARK else ReaderColorTheme.LIGHT

data class ReaderColors(val background: Color, val foreground: Color)

val ReaderColorTheme.readerColors: ReaderColors
    get() = when (this) {
        ReaderColorTheme.LIGHT -> ReaderColors(Color.White, Color(0xFF1A1A1A))
        ReaderColorTheme.SEPIA -> ReaderColors(Color(0xFFFBF0D9), Color(0xFF4A3728))
        ReaderColorTheme.DARK -> ReaderColors(Color(0xFF1E1E2E), Color(0xFFCDD6F4))
        ReaderColorTheme.BLACK -> ReaderColors(Color.Black, Color(0xFFCCCCCC))
    }

val ReaderFont.fontFamily: FontFamily
    get() = when (this) {
        ReaderFont.DEFAULT -> FontFamily.Default
        ReaderFont.SERIF -> FontFamily.Serif
        ReaderFont.MONOSPACE -> FontFamily.Monospace
    }

/** Resolved alignment + direction for the reader's chapter text. */
data class ReaderTextLayout(val textAlign: TextAlign, val textDirection: TextDirection)

/**
 * Resolves [align] for a novel whose language is right-to-left when [rtlLanguage].
 *
 * AUTO leans on paragraph content (`ContentOr*`): each paragraph picks its direction from its
 * own first strong character, falling back to the novel's language — so an English quote inside
 * an Arabic novel still reads LTR. LEFT / RIGHT force the direction outright; CENTER / JUSTIFY
 * only change alignment and keep the content-derived direction.
 */
fun readerTextLayout(align: ReaderTextAlign, rtlLanguage: Boolean): ReaderTextLayout {
    val contentDirection = if (rtlLanguage) TextDirection.ContentOrRtl else TextDirection.ContentOrLtr
    return when (align) {
        ReaderTextAlign.AUTO -> ReaderTextLayout(TextAlign.Start, contentDirection)
        ReaderTextAlign.LEFT -> ReaderTextLayout(TextAlign.Left, TextDirection.Ltr)
        ReaderTextAlign.RIGHT -> ReaderTextLayout(TextAlign.Right, TextDirection.Rtl)
        ReaderTextAlign.CENTER -> ReaderTextLayout(TextAlign.Center, contentDirection)
        ReaderTextAlign.JUSTIFY -> ReaderTextLayout(TextAlign.Justify, contentDirection)
    }
}
