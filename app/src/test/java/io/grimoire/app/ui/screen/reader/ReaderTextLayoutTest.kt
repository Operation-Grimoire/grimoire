package io.grimoire.app.ui.screen.reader

import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import io.grimoire.api.model.lang.Language
import io.grimoire.app.data.local.entity.ReaderTextAlign
import org.junit.Assert.assertEquals
import org.junit.Test

class ReaderTextLayoutTest {

    @Test
    fun `auto on an RTL language starts right and prefers RTL content`() {
        val layout = resolveReaderTextLayout(ReaderTextAlign.AUTO, Language.AR)
        assertEquals(TextAlign.Start, layout.textAlign)
        assertEquals(TextDirection.ContentOrRtl, layout.textDirection)
    }

    @Test
    fun `auto on an LTR language prefers LTR content`() {
        val layout = resolveReaderTextLayout(ReaderTextAlign.AUTO, Language.EN)
        assertEquals(TextAlign.Start, layout.textAlign)
        assertEquals(TextDirection.ContentOrLtr, layout.textDirection)
    }

    @Test
    fun `auto with unknown language falls back to LTR content`() {
        val layout = resolveReaderTextLayout(ReaderTextAlign.AUTO, null)
        assertEquals(TextDirection.ContentOrLtr, layout.textDirection)
    }

    @Test
    fun `all RTL scripts resolve to RTL content direction`() {
        listOf(Language.AR, Language.HE, Language.FA, Language.UR).forEach { lang ->
            val layout = resolveReaderTextLayout(ReaderTextAlign.AUTO, lang)
            assertEquals("$lang", TextDirection.ContentOrRtl, layout.textDirection)
        }
    }

    @Test
    fun `left and right force their direction outright`() {
        assertEquals(
            ReaderTextLayout(TextAlign.Left, TextDirection.Ltr),
            resolveReaderTextLayout(ReaderTextAlign.LEFT, Language.AR),
        )
        assertEquals(
            ReaderTextLayout(TextAlign.Right, TextDirection.Rtl),
            resolveReaderTextLayout(ReaderTextAlign.RIGHT, Language.EN),
        )
    }

    @Test
    fun `center and justify keep the language-derived direction`() {
        assertEquals(
            ReaderTextLayout(TextAlign.Center, TextDirection.ContentOrRtl),
            resolveReaderTextLayout(ReaderTextAlign.CENTER, Language.FA),
        )
        assertEquals(
            ReaderTextLayout(TextAlign.Justify, TextDirection.ContentOrLtr),
            resolveReaderTextLayout(ReaderTextAlign.JUSTIFY, Language.ZH),
        )
    }
}
