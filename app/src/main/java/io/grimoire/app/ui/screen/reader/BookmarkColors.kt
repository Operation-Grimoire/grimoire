package io.grimoire.app.ui.screen.reader

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Colours for in-chapter bookmarks (#132), derived from the active Material theme so
 * they match the app's palette. Each bookmark in a chapter is assigned a distinct
 * index; colours cycle once exhausted.
 */
object BookmarkColors {
    /** First palette slot not already used in the chapter, else cycles. */
    fun nextIndex(used: Collection<Int>, paletteSize: Int = DEFAULT_PALETTE_SIZE): Int {
        for (i in 0 until paletteSize) if (i !in used) return i
        return used.size.mod(paletteSize)
    }

    const val DEFAULT_PALETTE_SIZE = 6
}

@Composable
@ReadOnlyComposable
fun bookmarkPalette(): List<Color> {
    val cs = MaterialTheme.colorScheme
    return listOf(cs.primary, cs.tertiary, cs.secondary, cs.error, cs.inversePrimary, cs.tertiaryContainer)
}

@Composable
@ReadOnlyComposable
fun bookmarkColor(index: Int): Color = bookmarkPalette().let { it[index.mod(it.size)] }
