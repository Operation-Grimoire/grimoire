package io.grimoire.desktop

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.Chapter
import io.grimoire.api.model.novel.Novel
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.api.model.novel.NovelStatus
import io.grimoire.api.model.novel.PageContent
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.decodeChapterContent
import io.grimoire.reading.encodeChapterContent
import org.json.JSONArray
import org.json.JSONObject
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

data class ReadPosition(val chapterUrl: String, val paragraph: Int, val offset: Int)
data class SavedBook(
    val key: NovelKey,
    val novel: Novel,
    val chapters: List<Chapter>,
    val favorite: Boolean = false,
    val downloaded: Set<String> = emptySet(),
    val position: ReadPosition? = null,
)

/** Desktop storage adapter. URLs are stored as keys, never used as filesystem paths. */
class LocalLibrary(private val directory: Path) {
    private val file = directory.resolve("library.json")
    private val books = linkedMapOf<NovelKey, SavedBook>()

    init {
        Files.createDirectories(directory)
        if (Files.exists(file)) {
            val root = JSONObject(Files.readString(file))
            require(root.getInt("schema") == 1) { "Unsupported desktop library format" }
            val saved = root.getJSONArray("books")
            for (index in 0 until saved.length()) {
                val book = decodeBook(saved.getJSONObject(index))
                require(books.put(book.key, book) == null) { "Duplicate desktop novel identity" }
            }
        }
    }

    @Synchronized fun saved(): List<SavedBook> = books.values.filter { it.favorite }
    @Synchronized fun book(key: NovelKey): SavedBook? = books[key]

    @Synchronized fun remember(key: NovelKey, novel: Novel, chapters: List<Chapter>): SavedBook {
        require(novel.url == key.url && chapters.map { it.url }.distinct().size == chapters.size)
        val old = books[key]
        val book = SavedBook(key, novel, chapters, old?.favorite ?: false, old?.downloaded.orEmpty(), old?.position)
        books[key] = book
        flush()
        return book
    }

    @Synchronized fun favorite(key: NovelKey, value: Boolean): SavedBook {
        val book = books.getValue(key).copy(favorite = value)
        books[key] = book
        flush()
        return book
    }

    @Synchronized fun download(key: ChapterKey, pages: List<NovelPage>) {
        val book = books.getValue(key.novel)
        require(book.chapters.any { it.url == key.url && !it.locked }) { "Chapter is unavailable or locked" }
        require(pages.any { (it.content as? PageContent.Text)?.text?.isNotBlank() == true }) { "Chapter has no readable text" }
        val content = encodeChapterContent(pages)
        val root = JSONObject().put("schema", 1).put("sourceId", key.novel.sourceId)
            .put("novelUrl", key.novel.url).put("chapterUrl", key.url)
            .put("content", content).put("sha256", sha256(content.toByteArray(Charsets.UTF_8)))
        atomicWrite(contentFile(key), root.toString())
        books[key.novel] = book.copy(downloaded = book.downloaded + key.url)
        flush()
    }

    /** No Source or network dependency: this path also works with the extension removed. */
    @Synchronized fun downloaded(key: ChapterKey): List<NovelPage>? {
        val book = books[key.novel] ?: return null
        if (key.url !in book.downloaded) return null
        require(book.chapters.any { it.url == key.url && !it.locked }) { "Chapter is unavailable or locked" }
        val root = JSONObject(Files.readString(contentFile(key)))
        require(root.getInt("schema") == 1 && root.getLong("sourceId") == key.novel.sourceId &&
            root.getString("novelUrl") == key.novel.url && root.getString("chapterUrl") == key.url) { "Downloaded chapter identity mismatch" }
        val content = root.getString("content")
        require(root.getString("sha256") == sha256(content.toByteArray(Charsets.UTF_8))) { "Downloaded chapter is corrupt" }
        return decodeChapterContent(content)
    }

    /** Update memory synchronously; UI debounces disk flush and flushes on navigation/close. */
    @Synchronized fun position(key: ChapterKey, paragraph: Int, offset: Int) {
        require(paragraph >= 0 && offset >= 0)
        val book = books.getValue(key.novel)
        require(book.chapters.any { it.url == key.url && !it.locked })
        books[key.novel] = book.copy(position = ReadPosition(key.url, paragraph, offset))
    }

    @Synchronized fun flush() {
        val root = JSONObject().put("schema", 1).put("books", JSONArray(books.values.map(::encodeBook)))
        atomicWrite(file, root.toString())
    }

    private fun contentFile(key: ChapterKey): Path {
        val identity = JSONArray(listOf(key.novel.sourceId, key.novel.url, key.url)).toString()
        return directory.resolve("chapter-${sha256(identity.toByteArray(Charsets.UTF_8))}.json")
    }
}

private fun encodeBook(book: SavedBook): JSONObject = JSONObject()
    .put("sourceId", book.key.sourceId).put("url", book.key.url).put("title", book.novel.title)
    .put("language", book.novel.language.name).put("author", book.novel.author)
    .put("description", book.novel.description).put("favorite", book.favorite)
    .put("downloaded", JSONArray(book.downloaded.toList()))
    .put("chapters", JSONArray(book.chapters.map { chapter -> JSONObject()
        .put("url", chapter.url).put("name", chapter.name).put("locked", chapter.locked)
        .put("number", chapter.chapterNumber).put("uploadDate", chapter.uploadDate) }))
    .apply { book.position?.let { put("position", JSONObject().put("chapterUrl", it.chapterUrl)
        .put("paragraph", it.paragraph).put("offset", it.offset)) } }

private fun decodeBook(root: JSONObject): SavedBook {
    val key = NovelKey(root.getLong("sourceId"), root.getString("url"))
    val novel = Novel(key.url, root.getString("title"), Language.valueOf(root.getString("language")),
        author = root.optString("author").takeIf(String::isNotEmpty),
        description = root.optString("description").takeIf(String::isNotEmpty),
        status = NovelStatus.UNKNOWN, initialized = true)
    val chapters = root.getJSONArray("chapters").let { array -> (0 until array.length()).map { index ->
        val c = array.getJSONObject(index)
        Chapter(c.getString("url"), c.getString("name"), c.getLong("uploadDate"), c.getFloat("number"), locked = c.getBoolean("locked"))
    } }
    val downloaded = root.getJSONArray("downloaded").let { array -> (0 until array.length()).map(array::getString).toSet() }
    val position = root.optJSONObject("position")?.let {
        ReadPosition(it.getString("chapterUrl"), it.getInt("paragraph"), it.getInt("offset")).also { p ->
            require(p.paragraph >= 0 && p.offset >= 0)
        }
    }
    return SavedBook(key, novel, chapters, root.getBoolean("favorite"), downloaded, position)
}

private fun atomicWrite(destination: Path, text: String) {
    val temporary = Files.createTempFile(destination.parent, ".grimoire-", ".tmp")
    try {
        Files.writeString(temporary, text)
        try { Files.move(temporary, destination, ATOMIC_MOVE, REPLACE_EXISTING) }
        catch (_: AtomicMoveNotSupportedException) { Files.move(temporary, destination, REPLACE_EXISTING) }
    } finally { Files.deleteIfExists(temporary) }
}
