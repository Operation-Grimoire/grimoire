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
    fun parseSearch_handlesMobileLayout() {
        // NU serves a different DOM to mobile UAs (the app sends an Android
        // UA): .search_title is a direct child of .search_main_box_nu with an
        // extra " mb" class, not nested under .search_body_nu.
        val html = """
            <html><body>
              <div class="search_main_box_nu mb">
                <div class="search_title mb">
                  <span id="sid61957" class="rl_icons_en mb"></span>
                  <a href="https://www.novelupdates.com/series/circle-of-inevitability/">Circle of Inevitability</a>
                </div>
                <div class="search_img_nu mb"><img src="https://cdn.test/c.jpg"></div>
              </div>
            </body></html>
        """.trimIndent()

        val results = NovelUpdatesParser.parseSearch(
            Jsoup.parse(html, NovelUpdatesEndpoints.BASE_URL),
        )

        assertEquals(1, results.size)
        assertEquals("Circle of Inevitability", results[0].title)
        assertEquals("circle-of-inevitability", results[0].slug)
        assertEquals("https://cdn.test/c.jpg", results[0].coverUrl)
    }

    @Test
    fun parseSeries_extractsRecommendationsAndStopsAtNextSection() {
        val html = """
            <html><body>
              <div class="seriestitlenu">Circle of Inevitability</div>
              <h5 class="seriesother">Related Series</h5>
              <a href="https://www.novelupdates.com/series/lord-of-the-mysteries/">Lord of the Mysteries</a> (Prequel)<br/>
              <h5 class="seriesother">Recommendations</h5>
              <a class="genre" id="sid56760" href="https://www.novelupdates.com/series/deep-sea-embers/" title="Recommended by 1 users">Deep Sea Embers</a> (1)<br/>
              <a class="genre" id="sid19326" href="https://www.novelupdates.com/series/my-house-of-horrors/" title="Recommended by 1 users">My House of Horrors</a> (1)<br/>
              <h5 class="seriesother">Recommendation Lists</h5>
              <a href="https://www.novelupdates.com/series/should-not-appear/">Should Not Appear</a>
            </body></html>
        """.trimIndent()

        val series = NovelUpdatesParser.parseSeries(
            Jsoup.parse(html, NovelUpdatesEndpoints.BASE_URL),
            "https://www.novelupdates.com/series/circle-of-inevitability/",
        )

        assertEquals("Circle of Inevitability", series.title)
        assertEquals(2, series.recommendations.size)
        assertEquals("Deep Sea Embers", series.recommendations[0].title)
        assertEquals("My House of Horrors", series.recommendations[1].title)
        assertTrue(series.recommendations.none { it.title == "Should Not Appear" })
        assertTrue(series.recommendations.none { it.title == "Lord of the Mysteries" })
        // Cover derived from the anchor's id="sidNNNNN" via the CDN pattern.
        assertEquals(
            "https://cdn.novelupdates.com/imgmid/series_56760.jpg",
            series.recommendations[0].coverUrl,
        )
    }

    @Test
    fun parseListing_reusesSearchCardExtraction() {
        val html = """
            <html><body>
              <div class="search_main_box_nu">
                <div class="search_img_nu"><img src="https://cdn.test/a.jpg"></div>
                <div class="search_body_nu">
                  <div class="search_title">
                    <a href="https://www.novelupdates.com/series/overgeared/">Overgeared</a>
                  </div>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val results = NovelUpdatesParser.parseListing(
            Jsoup.parse(html, NovelUpdatesEndpoints.BASE_URL),
        )

        assertEquals(1, results.size)
        assertEquals("Overgeared", results[0].title)
        assertEquals("overgeared", results[0].slug)
    }

    @Test
    fun hasNextPage_detectsPaginationNextLink() {
        val withNext = Jsoup.parse(
            """<html><body><div class="digg_pagination">
                 <a class="next_page" href="?pg=2">Next »</a>
               </div></body></html>""",
            NovelUpdatesEndpoints.BASE_URL,
        )
        val withoutNext = Jsoup.parse(
            """<html><body><div class="digg_pagination">
                 <span class="current">1</span>
               </div></body></html>""",
            NovelUpdatesEndpoints.BASE_URL,
        )

        assertTrue(NovelUpdatesParser.hasNextPage(withNext))
        assertTrue(!NovelUpdatesParser.hasNextPage(withoutNext))
    }

    @Test
    fun parseRanking_takesScopedSeriesLinksInOrderAndIgnoresSidebar() {
        val html = """
            <html><body>
              <table id="myTable">
                <tr><td>1</td><td>
                  <a id="sid12345" href="https://www.novelupdates.com/series/shadow-slave/">Shadow Slave</a>
                </td></tr>
                <tr><td>2</td><td>
                  <a href="https://www.novelupdates.com/series/the-beginning-after-the-end/">The Beginning After The End</a>
                </td></tr>
              </table>
              <div class="sidebar">
                <a href="https://www.novelupdates.com/series/should-not-appear/">Should Not Appear</a>
              </div>
            </body></html>
        """.trimIndent()

        val results = NovelUpdatesParser.parseRanking(
            Jsoup.parse(html, NovelUpdatesEndpoints.BASE_URL),
        )

        assertEquals(2, results.size)
        assertEquals("Shadow Slave", results[0].title)
        assertEquals("shadow-slave", results[0].slug)
        assertEquals(
            "https://cdn.novelupdates.com/imgmid/series_12345.jpg",
            results[0].coverUrl,
        )
        assertEquals("the-beginning-after-the-end", results[1].slug)
        assertTrue(results.none { it.title == "Should Not Appear" })
    }

    @Test
    fun parseSeries_extractsAuthorsReviewsAndSid() {
        val html = """
            <html><body>
              <div class="seriestitlenu">The Death Mage</div>
              <h5 class="seriesother">Author(s)</h5>
              <div id="showauthors">
                <a class="genre" id="authtag" href="https://www.novelupdates.com/nauthor/densuke/">Densuke</a><br>
                <a class="genre" id="authtag" href="https://www.novelupdates.com/nauthor/x/">デンスケ</a><br>
              </div>

              <div class="w-comments-item" id="comment-115813">
                <div class="rev_left">
                  <img alt='Donce' src='https://av.test/donce.jpg' class='avatar'>
                </div>
                <div class="w-comments-item-meta-new">
                  <table><tbody><tr>
                    <td valign="top">
                      <a class="revname115813" href="https://www.novelupdates.com/user/207649/Donce/">Donce</a>
                      <i class="fa fa-star"></i><i class="fa fa-star"></i><i class="fa fa-star"></i><i class="fa fa-star"></i><i class="fa fa-star"></i>
                    </td>
                    <td style="text-align: right;" valign="top">
                      <div style="text-align: right;">Sep 01, 2019</div>
                      <div>Status: <span id="stat115813">c200</span></div>
                    </td>
                  </tr></tbody></table>
                </div>
                <div class="w-comments-item-text 207649">
                  First paragraph.<p></p>
                  Second paragraph.
                  <span class="dots">... </span><span class="morelink" onclick="showtext(this); return false;">more&gt;&gt;</span>
                  <span style="display:none"> Hidden continuation.<span class="morelink"> &lt;&lt;less</span></span>
                </div>
              </div>
              <div class="rev_b1">
                <span class="rev_bar"><span class="liked_115813">50</span> Likes</span>
                <span><a class="permrev" href="//www.novelupdates.com/fdrev/?comid=115813&sid=8456">Permalink</a></span>
              </div>

              <div class="w-comments-item" id="comment-42685">
                <div class="rev_left"><img alt='Dark_Messiah' src='https://av.test/dm.png' class='avatar'></div>
                <div class="w-comments-item-meta-new">
                  <table><tbody><tr>
                    <td valign="top">
                      <a class="revname42685" href="https://www.novelupdates.com/user/31488/Dark_Messiah/">Dark_Messiah</a>
                      <i class="fa fa-star"></i><i class="fa fa-star"></i><i class="fa fa-star"></i><i class="fa fa-star-o"></i><i class="fa fa-star-o"></i>
                    </td>
                    <td style="text-align: right;" valign="top">
                      <div style="text-align: right;">Jun 15, 2017</div>
                      <div>Status: <span id="stat42685">--</span></div>
                    </td>
                  </tr></tbody></table>
                </div>
                <div class="w-comments-item-text 31488">Short take.</div>
              </div>
              <div class="rev_b1">
                <span class="rev_bar"><span class="liked_42685">12</span> Likes</span>
                <span><a class="permrev" href="//www.novelupdates.com/fdrev/?comid=42685&sid=8456">Permalink</a></span>
              </div>

              <div class="w-comments-pagination">
                <span class='page-numbers current'>1</span>
                <a class='page-numbers' href='#'>2</a>
                <a class='page-numbers' href='#'>18</a>
                <a class="next page-numbers" href='#'>Next &raquo;</a>
              </div>
            </body></html>
        """.trimIndent()

        val series = NovelUpdatesParser.parseSeries(
            Jsoup.parse(html, NovelUpdatesEndpoints.BASE_URL),
            "https://www.novelupdates.com/series/the-death-mage/",
        )

        assertEquals(listOf("Densuke", "デンスケ"), series.authors)
        assertEquals("8456", series.sid)
        assertEquals(18, series.reviewPageCount)
        assertEquals(2, series.reviews.size)

        val first = series.reviews[0]
        assertEquals("115813", first.id)
        assertEquals("Donce", first.author)
        assertEquals(5, first.rating)
        assertEquals("Sep 01, 2019", first.date)
        assertEquals("c200", first.progress)
        assertEquals(50, first.likes)
        assertTrue(first.body.contains("First paragraph."))
        assertTrue(first.body.contains("Hidden continuation."))
        assertTrue(!first.body.contains("more>>"))
        assertEquals(
            "https://www.novelupdates.com/fdrev/?comid=115813&sid=8456",
            first.permalink,
        )

        val second = series.reviews[1]
        assertEquals(3, second.rating)
        assertEquals(null, second.progress)
    }

    @Test
    fun parseSeries_extractsTypeLanguageAndOgFallbacks() {
        val html = """
            <html><head>
              <meta property="og:image" content="https://cdn.test/og-cover.jpg" />
              <meta property="og:description" content="Fallback description." />
              <meta property="og:title" content="OG Title" />
            </head><body>
              <div class="w-blog post-8456 language-japanese ntype-web-novel genre-action">
                <div class="seriestitlenu"></div>
                <div id="showtype">
                  <a class="genre type" href="https://www.novelupdates.com/ntype/web-novel/">Web Novel</a>
                  <span>(JP)</span>
                </div>
              </div>
            </body></html>
        """.trimIndent()

        val series = NovelUpdatesParser.parseSeries(
            Jsoup.parse(html, NovelUpdatesEndpoints.BASE_URL),
            "https://www.novelupdates.com/series/x/",
        )

        assertEquals("Web Novel (JP)", series.type)
        assertEquals("Japanese", series.language)
        // Title/description/cover fall back to the og: meta tags when the
        // in-page elements are empty/missing.
        assertEquals("OG Title", series.title)
        assertEquals("Fallback description.", series.description)
        assertEquals("https://cdn.test/og-cover.jpg", series.coverUrl)
    }

    @Test
    fun parseSearch_emptyOnUnrelatedHtml() {
        val results = NovelUpdatesParser.parseSearch(
            Jsoup.parse("<html><body><p>nothing here</p></body></html>"),
        )
        assertTrue(results.isEmpty())
    }
}
