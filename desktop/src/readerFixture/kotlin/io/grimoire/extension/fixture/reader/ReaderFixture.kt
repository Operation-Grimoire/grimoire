package io.grimoire.extension.fixture.reader

import io.grimoire.api.model.filter.Filter
import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.api.source.SourceInfo
import io.grimoire.api.source.feature.LatestSource
import io.grimoire.api.source.feature.PopularSource
import io.grimoire.api.source.feature.SearchSource
import io.grimoire.api.source.http.HttpSource
import io.grimoire.api.source.web.ChapterListSource
import io.grimoire.api.source.web.PageListSource

/** Test-only extension: fixed API values, no website or parser. Loaded only from its own JAR. */
@SourceInfo(name = "Reader fixture", lang = Language.EN, baseUrl = "https://reader.invalid", versionCode = 1)
class ReaderFixture : HttpSource(), PopularSource, LatestSource, SearchSource, ChapterListSource, PageListSource {
    override val name = "Reader fixture"
    override val lang = Language.EN
    override val baseUrl = "https://reader.invalid"

    // Page 2 repeats a title to exercise de-duplication; page 3 is empty.
    override suspend fun getPopularNovels(page: Int): List<Novel> {
        request("/catalog")
        return when (page) {
            1 -> listOf(Novel("/novel", "A fixture novel", Language.EN))
            2 -> listOf(Novel("/novel", "A fixture novel", Language.EN), Novel("/second", "A second fixture novel", Language.EN))
            else -> emptyList()
        }
    }

    override suspend fun getLatestUpdates(page: Int): List<Novel> {
        request("/catalog")
        return if (page == 1) listOf(Novel("/latest", "A recently updated novel", Language.EN)) else emptyList()
    }

    override suspend fun searchNovels(query: String, page: Int, filters: List<Filter<*>>): List<Novel> {
        request("/catalog")
        val titles = listOf(Novel("/novel", "A fixture novel", Language.EN), Novel("/second", "A second fixture novel", Language.EN))
        return if (page == 1) titles.filter { it.title.contains(query, ignoreCase = true) } else emptyList()
    }

    override suspend fun getNovelDetails(novel: Novel): Novel {
        request("/details")
        return novel.copy(initialized = true, description = "A deterministic reader test.")
    }

    override suspend fun getChapterList(novel: Novel): List<Chapter> {
        request("/chapters")
        return listOf(Chapter("/chapter", "First chapter"), Chapter("/locked", "Locked chapter", locked = true))
    }

    override suspend fun getPageList(chapter: Chapter): List<NovelPage> {
        request(chapter.url)
        return listOf(NovelPage(0, PageContent.Text("The first paragraph.", "<p>The first paragraph.</p>")),
            NovelPage(1, PageContent.Separator()), NovelPage(2, PageContent.Text("The final paragraph.")))
    }

    private suspend fun request(path: String) {
        get("$baseUrl$path").use { check(it.isSuccessful) { "Fixture request failed" } }
    }
}
