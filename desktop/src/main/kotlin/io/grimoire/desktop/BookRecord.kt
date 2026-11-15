package io.grimoire.desktop

import io.grimoire.api.model.lang.Language
import io.grimoire.api.model.novel.*
import io.grimoire.reading.NovelKey
import org.json.JSONArray
import org.json.JSONObject
internal fun encodeBook(book: SavedBook): JSONObject = JSONObject()
    .put("sourceId", book.key.sourceId).put("url", book.key.url).put("title", book.novel.title)
    .put("language", book.novel.language.name).put("author", book.novel.author)
    .put("description", book.novel.description).put("favorite", book.favorite)
    .put("downloaded", JSONArray(book.downloaded.toList()))
    .put("chapters", JSONArray(book.chapters.map { chapter -> JSONObject()
        .put("url", chapter.url).put("name", chapter.name).put("locked", chapter.locked)
        .put("number", chapter.chapterNumber).put("uploadDate", chapter.uploadDate) }))
    .apply { book.position?.let { put("position", JSONObject().put("chapterUrl", it.chapterUrl)
        .put("paragraph", it.paragraph).put("offset", it.offset)) } }

internal fun decodeBook(root: JSONObject): SavedBook {
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
