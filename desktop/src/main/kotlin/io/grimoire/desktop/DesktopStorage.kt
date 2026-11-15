package io.grimoire.desktop

import io.grimoire.reading.ChapterKey
import io.grimoire.reading.StoredChapterContent
import io.grimoire.reading.storage.SavedChapterDao
import io.grimoire.reading.storage.SavedChapterRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Retains the existing file identity, digest, download-index and locked-chapter checks. */
class FileSavedChapterDao(private val library: LocalLibrary) : SavedChapterDao {
    override suspend fun read(key: ChapterKey): StoredChapterContent? = withContext(Dispatchers.IO) {
        library.readSavedContent(key)
    }
}

/** Desktop composition root: backend selection stays outside the reader workflow. */
fun desktopReaderModel(library: LocalLibrary, hostApi: String, networkEnabled: AtomicBoolean): ReaderModel =
    ReaderModel(library, hostApi, networkEnabled, SavedChapterRepository(FileSavedChapterDao(library)))
