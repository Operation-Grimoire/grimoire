package io.grimoire.reading

/** Source URLs are opaque identities. Database row IDs are device-local. */
data class NovelKey(val sourceId: Long, val url: String) {
    init {
        require(sourceId >= 0 && url.isNotBlank())
    }
}

data class ChapterKey(val novel: NovelKey, val url: String) {
    init { require(url.isNotBlank()) }
}
