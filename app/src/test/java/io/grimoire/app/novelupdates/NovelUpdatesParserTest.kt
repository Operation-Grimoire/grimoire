package io.grimoire.app.novelupdates

import io.grimoire.app.data.novelupdates.NovelUpdatesEndpoints
import io.grimoire.app.data.novelupdates.NovelUpdatesParser
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelUpdatesParserTest {

    @Test
    fun slugFromUrl_extractsSeriesSlug() {
        assertEquals(
            "lord-of-mysteries",
            NovelUpdatesEndpoints.slugFromUrl("https://www.novelupdates.com/series/lord-of-mysteries/"),
        )
        assertEquals(
            "lord-of-mysteries",
            NovelUpdatesEndpoints.slugFromUrl("/series/lord-of-mysteries/"),
        )
    }

    @Test
    fun parseSearch_extractsResults() {
        val html = """
            <html><body>
              <div class="search_main_box_nu">
                <div class="search_img_nu"><img src="https://cdn.test/cover1.jpg"></div>
                <div class="search_body_nu">
                  <div class="search_title">
                    <a href="https://www.novelupdates.com/series/lord-of-mysteries/">Lord of Mysteries</a>
                  </div>
                </div>
              </div>
              <div class="search_main_box_nu">
                <div class="search_img_nu"><img src="https://cdn.test/cover2.jpg"></div>
                <div class="search_body_nu">
                  <div class="search_title">
                    <a href="https://www.novelupdates.com/series/release-that-witch/">Release that Witch</a>
                  </div>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val results = NovelUpdatesParser.parseSearch(
            Jsoup.parse(html, NovelUpdatesEndpoints.BASE_URL),
        )

        assertEquals(2, results.size)
        assertEquals("Lord of Mysteries", results[0].title)
        assertEquals("lord-of-mysteries", results[0].slug)
        assertEquals("https://cdn.test/cover1.jpg", results[0].coverUrl)
        assertEquals("release-that-witch", results[1].slug)
    }

    @Test
    fun parseSearch_emptyOnUnrelatedHtml() {
        val results = NovelUpdatesParser.parseSearch(
            Jsoup.parse("<html><body><p>nothing here</p></body></html>"),
        )
        assertTrue(results.isEmpty())
    }
}
