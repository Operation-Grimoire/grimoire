package io.grimoire.desktop

import io.grimoire.reading.ChapterLoader
import io.grimoire.reading.ChapterKey
import io.grimoire.reading.NovelKey
import io.grimoire.reading.StoredChapterContent
import io.grimoire.reading.storage.*
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** SQL-backed adapters implement the shared contracts; source access stays in the host. */
class DesktopSavedChapterDao(private val library: LocalLibrary) : SavedChapterDao {
    override suspend fun readObserved(key: ChapterKey): ObservedSavedChapter = withContext(Dispatchers.IO) {
        val context = kotlinx.coroutines.currentCoroutineContext()
        library.readObserved(key) { context.ensureActive() }
    }
    override suspend fun isObservationCurrent(observation: ChapterStorageObservation): Boolean = withContext(Dispatchers.IO) {
        val context = kotlinx.coroutines.currentCoroutineContext()
        library.isObservationCurrent(observation) { context.ensureActive() }
    }
    override suspend fun readSnapshot(key: ChapterKey): SavedChapterSnapshot? = withContext(Dispatchers.IO) {
        library.readSnapshot(key)
    }
    override suspend fun read(key: ChapterKey): StoredChapterContent? = withContext(Dispatchers.IO) {
        library.readSavedContent(key)
    }
}

class DesktopLibraryDao(private val library: LocalLibrary) : LibraryDao {
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
    ReaderModel(library, hostApi, networkEnabled, ChapterLoader(SavedChapterRepository(DesktopSavedChapterDao(library))),
        LibraryRepository(DesktopLibraryDao(library)), DownloadRepository(DesktopDownloadDao(library)))

class DesktopDownloadDao(private val library: LocalLibrary) : DownloadDao {
    override suspend fun claim(key: ChapterKey): DownloadAttempt? = withContext(Dispatchers.IO) {
        val context = kotlinx.coroutines.currentCoroutineContext()
        library.claim(key) { context.ensureActive() }
    }
    override suspend fun commit(attempt: DownloadAttempt, prepared: PreparedDownload): DownloadCommit = withContext(Dispatchers.IO) {
        val context = kotlinx.coroutines.currentCoroutineContext()
        library.commit(attempt, prepared) { context.ensureActive() }
    }
    override suspend fun fail(attempt: DownloadAttempt, failure: DownloadFailure): Boolean = withContext(Dispatchers.IO) {
        val context = kotlinx.coroutines.currentCoroutineContext()
        library.fail(attempt) { context.ensureActive() }
    }
    override suspend fun currentReceipt(key: ChapterKey): DownloadReceipt? = withContext(Dispatchers.IO) {
        library.currentReceipt(key)
    }
}

/** Additive durable checkpoint adapter; reader capture/restore adoption is a separate slice. */
class DesktopReadingCheckpointDao(private val library: LocalLibrary) : ReadingCheckpointDao {
    override suspend fun register(observation: ChapterStorageObservation,
        document: io.grimoire.reading.anchor.CanonicalDocument): ReadingSession? = withContext(Dispatchers.IO) {
        val context = kotlinx.coroutines.currentCoroutineContext()
        library.registerReadingSession(observation, document) { context.ensureActive() }
    }
    override suspend fun commit(command: CheckpointCommand): CheckpointCommit = withContext(Dispatchers.IO) {
        val context = kotlinx.coroutines.currentCoroutineContext()
        library.commitReadingCheckpoint(command) { context.ensureActive() }
    }
    override suspend fun read(key: ChapterKey): io.grimoire.reading.anchor.ReadingAnchor? = withContext(Dispatchers.IO) {
        library.readReadingCheckpoint(key)
    }
}

class DesktopReadingStateDao(private val library: LocalLibrary) : ReadingStateDao {
    override suspend fun prepare(key: ChapterKey): PreparedReadingState? = withContext(Dispatchers.IO) {
        val context = kotlinx.coroutines.currentCoroutineContext()
        library.prepareReadingState(key) { context.ensureActive() }
    }
    override suspend fun register(binding: ReadingStateBinding, content: ChapterStorageObservation,
        document: io.grimoire.reading.anchor.CanonicalDocument, kind: ReadingStateWriterKind): ReadingStateSession? = withContext(Dispatchers.IO) {
        val context = kotlinx.coroutines.currentCoroutineContext()
        library.registerStateWriter(binding, content, document, kind) { context.ensureActive() }
    }
    override suspend fun commit(command: ReadingStateCommand): ReadingStateCommit = withContext(Dispatchers.IO) {
        val context = kotlinx.coroutines.currentCoroutineContext()
        library.commitReadingState(command) { context.ensureActive() }
    }
    override suspend fun read(key: ChapterKey): io.grimoire.reading.state.ReadingCompletionState? = withContext(Dispatchers.IO) { library.readReadingState(key) }
}
