package io.grimoire.extension.fixture.reader

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.PageContent
import io.grimoire.api.source.SourceInfo
import io.grimoire.api.source.feature.PopularSource
import io.grimoire.api.source.http.HttpSource
import io.grimoire.api.source.web.ChapterListSource
import io.grimoire.api.source.web.PageListSource

/** Test-only extension: fixed API values, no website or parser. Loaded only from its own JAR. */
@SourceInfo(name = "Reader fixture", lang = Language.EN, baseUrl = "https://reader.invalid", versionCode = 1)
class ReaderFixture : HttpSource(), PopularSource, ChapterListSource, PageListSource {
    override val name = "Reader fixture"
    override val lang = Language.EN
    override val baseUrl = "https://reader.invalid"

    override suspend fun getPopularNovels(page: Int): List<Novel> {
        request("/catalog")
        return listOf(Novel("/novel", "A fixture novel", Language.EN))
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
