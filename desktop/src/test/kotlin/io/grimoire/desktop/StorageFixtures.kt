package io.grimoire.desktop

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.*
import io.grimoire.reading.*
import io.grimoire.reading.storage.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

internal val fixtureNovel = Novel("/novel", "A public novel", Language.EN)
internal val fixtureChapters = listOf(Chapter("/chapter", "First"), Chapter("/locked", "Locked", locked = true))
internal val fixtureKey = ChapterKey(NovelKey(17, "/novel"), "/chapter")
internal fun prose(text: String = "Faithful prose.") = listOf(NovelPage(0, PageContent.Text(text, "<b>$text</b>")))
internal fun LocalLibrary.seed() { remember(fixtureKey.novel, fixtureNovel, fixtureChapters) }
internal fun LocalLibrary.download(key: ChapterKey, pages: List<NovelPage>) = runBlocking {
    val repository = DownloadRepository(DesktopDownloadDao(this@download))
    val attempt = repository.claim(key) ?: throw IllegalArgumentException("Chapter is unavailable or locked")
    check(repository.commit(attempt, pages) is DownloadCommit.Committed)
}

// Frozen old writer schema. Deliberately does not call the new book/content serializers.
internal fun legacyFixture(directory: Path, version: Int = 1, body: String = "Legacy prose\u001c<i>Legacy prose</i>"): Path {
    Files.writeString(directory.resolve("library.json"), """{"schema":1,"books":[{"sourceId":17,"url":"/novel","title":"A public novel","language":"EN","favorite":true,"downloaded":["/chapter"],"chapters":[{"url":"/chapter","name":"First","locked":false,"number":-1,"uploadDate":0},{"url":"/locked","name":"Locked","locked":true,"number":-1,"uploadDate":0}],"position":{"chapterUrl":"/chapter","paragraph":0,"offset":17}}]}""")
    val identity = "[17,\"/novel\",\"/chapter\"]"
    val file = directory.resolve("chapter-${sha256(identity.toByteArray(Charsets.UTF_8))}.json")
    val record = JSONObject().put("schema", version).put("sourceId", 17).put("novelUrl", "/novel").put("chapterUrl", "/chapter")
        .put("content", body).put("sha256", sha256(body.toByteArray(Charsets.UTF_8)))
    if (version == 2) record.put("contentVersion", 2)
    Files.writeString(file, record.toString())
    return file
}

internal fun mutateDatabase(directory: Path, sql: String, vararg args: Any?) {
    DriverManager.getConnection("jdbc:sqlite:${directory.resolve("library.sqlite")}").use { it.execute(sql, *args) }
}
