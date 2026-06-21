package io.grimoire.app.ui.screen.reader

import androidx.compose.ui.graphics.Color

/**
 * Palette for in-chapter bookmarks (#132). Each bookmark in a chapter is assigned a
 * distinct colour by index; colours cycle once the palette is exhausted.
 */
object BookmarkColors {
    val palette: List<Color> = listOf(
        Color(0xFFFFCA28), // amber
        Color(0xFF66BB6A), // green
        Color(0xFF42A5F5), // blue
        Color(0xFFEF5350), // red
        Color(0xFFAB47BC), // purple
        Color(0xFFFF7043), // deep orange
        Color(0xFF26C6DA), // cyan
        Color(0xFFEC407A), // pink
    )

    fun color(index: Int): Color = palette[index.mod(palette.size)]

    /** First palette index not already used in the chapter, else cycles. */
    fun nextIndex(used: Collection<Int>): Int {
        for (i in palette.indices) if (i !in used) return i
        return used.size.mod(palette.size)
    }
}
