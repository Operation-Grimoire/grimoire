package io.grimoire.app.data.book

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BookFormatTest {

    @Test fun `recognized extensions take precedence over generic MIME types`() {
        assertEquals(BookFormat.EPUB, detectBookFormat("application/octet-stream", "BOOK.EPUB"))
        assertEquals(BookFormat.TXT, detectBookFormat("application/octet-stream", "novel.txt"))
    }

    @Test fun `known MIME types work without a filename`() {
        assertEquals(BookFormat.EPUB, detectBookFormat("application/epub+zip", null))
        assertEquals(BookFormat.TXT, detectBookFormat("text/plain; charset=UTF-8", null))
        assertEquals(BookFormat.TXT, detectBookFormat("TEXT/X-PLAIN", null))
    }

    @Test fun `unsupported or ambiguous input is rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            detectBookFormat("application/octet-stream", "novel.bin")
        }
        assertThrows(IllegalArgumentException::class.java) {
            detectBookFormat(null, null)
        }
    }
}
