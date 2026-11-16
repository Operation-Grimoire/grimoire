package io.grimoire.reading.storage

import io.grimoire.reading.ChapterKey

/** Presence is independent of readability: an empty saved body is still SAVED. */
enum class ChapterContentPresence { SAVED, ABSENT }

/**
 * Adapter-issued observation of an available chapter and its stored body in one transaction.
 * The scope identifies the observing store lifetime; generation is opaque, not a content hash
 * or cache locator. This is neither a portable anchor nor a registered session/write permit.
 */
data class ChapterStorageObservation(
    val key: ChapterKey,
    val scope: String,
    val generation: String,
    val presence: ChapterContentPresence,
) {
    init {
        require(scope.isNotBlank() && generation.isNotBlank()) { "Invalid storage observation" }
    }
}

/** Null observation is untracked; it must never be interpreted as certified absence. */
data class ObservedSavedChapter(val saved: SavedChapterSnapshot?, val observation: ChapterStorageObservation?) {
    init {
        require(observation == null || (saved != null) == (observation.presence == ChapterContentPresence.SAVED)) {
            "Stored content and observation presence disagree"
        }
    }
}
