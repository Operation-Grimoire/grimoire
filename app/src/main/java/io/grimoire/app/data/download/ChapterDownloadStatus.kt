package io.grimoire.app.data.download

/**
 * Lifecycle of a chapter download. The trailing three values (`REDOWNLOAD_*`) mirror
 * `QUEUED`/`DOWNLOADING`/`ERROR` but apply to a chapter that is already `DOWNLOADED` and
 * is being refreshed — keeping them as distinct states lets the UI show the chapter as
 * still-downloaded during the refresh, and lets cancel paths fall back to `DOWNLOADED`
 * rather than `NONE`.
 *
 * Ordinals are persisted in the DB; only ever APPEND new values to the end.
 */
enum class ChapterDownloadStatus {
    NONE,
    QUEUED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR,
    REDOWNLOAD_QUEUED,
    REDOWNLOADING,
    REDOWNLOAD_ERROR;

    companion object {
        /** States in which the row has saved `downloadedContent` — i.e. the reader can open it. */
        val HAS_CONTENT_ORDINALS: Set<Int> = setOf(
            DOWNLOADED.ordinal,
            REDOWNLOAD_QUEUED.ordinal,
            REDOWNLOADING.ordinal,
            REDOWNLOAD_ERROR.ordinal,
        )
        /** Sitting in the worker queue (either fresh or refresh). */
        val QUEUED_ORDINALS: Set<Int> = setOf(QUEUED.ordinal, REDOWNLOAD_QUEUED.ordinal)
        /** Currently being fetched by the worker (either fresh or refresh). */
        val DOWNLOADING_ORDINALS: Set<Int> = setOf(DOWNLOADING.ordinal, REDOWNLOADING.ordinal)
        /** Last attempt failed (either fresh or refresh). */
        val ERROR_ORDINALS: Set<Int> = setOf(ERROR.ordinal, REDOWNLOAD_ERROR.ordinal)
        /** Any active worker state — queued or in-flight. */
        val IN_FLIGHT_ORDINALS: Set<Int> = QUEUED_ORDINALS + DOWNLOADING_ORDINALS
    }
}
