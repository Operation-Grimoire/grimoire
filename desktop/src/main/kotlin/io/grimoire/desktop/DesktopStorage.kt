package io.grimoire.desktop

import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.StoredChapterContent
import io.grimoire.reading.storage.SavedChapterDao
import io.grimoire.reading.storage.SavedChapterRepository
import io.grimoire.reading.storage.LibraryDao
import io.grimoire.reading.storage.LibraryEntry
import io.grimoire.reading.storage.LibraryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** Retains the existing file identity, digest, download-index and locked-chapter checks. */
class FileSavedChapterDao(private val library: LocalLibrary) : SavedChapterDao {
    override suspend fun read(key: ChapterKey): StoredChapterContent? = withContext(Dispatchers.IO) {
        library.readSavedContent(key)
    }
}

class FileLibraryDao(private val library: LocalLibrary) : LibraryDao {
    override suspend fun find(key: NovelKey): LibraryEntry? = withContext(Dispatchers.IO) {
        library.book(key)?.toLibraryEntry()
    }

    override suspend fun setMembership(key: NovelKey, inLibrary: Boolean): LibraryEntry? = withContext(Dispatchers.IO) {
        library.updateMembership(key, inLibrary)?.toLibraryEntry()
    }
}

private fun SavedBook.toLibraryEntry() = LibraryEntry(key, novel.title, favorite, novel.author, novel.description)

/** Desktop composition root: backend selection stays outside the reader workflow. */
fun desktopReaderModel(library: LocalLibrary, hostApi: String, networkEnabled: AtomicBoolean): ReaderModel =
    ReaderModel(library, hostApi, networkEnabled, SavedChapterRepository(FileSavedChapterDao(library)),
        LibraryRepository(FileLibraryDao(library)))
