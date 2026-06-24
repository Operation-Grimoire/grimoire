package io.grimoire.app.data.tts

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.grimoire.api.model.novel.NovelPage
import io.grimoire.app.data.local.entity.ChapterEntity
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * UI-facing façade for read-aloud playback. Re-exposes [TtsPlaybackManager]'s state
 * flows and keeps service/intent plumbing out of ViewModels and Composables.
 */
@Singleton
class TtsController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: TtsPlaybackManager,
) {
    val state: StateFlow<TtsPlaybackState> = manager.state
    val nowPlaying: StateFlow<TtsNowPlaying?> = manager.nowPlaying
    val progress: StateFlow<TtsProgress> = manager.progress
    val errorMessage: StateFlow<String?> = manager.errorMessage

    /** Starts reading a chapter aloud, reusing already-loaded chapter data. */
    fun play(
        pkg: String,
        novelId: Long,
        chapterUrl: String,
        chapters: List<ChapterEntity>,
        startIndex: Int,
        pages: List<NovelPage>,
    ) {
        manager.prepare(pkg, novelId, chapterUrl, chapters, startIndex, pages)
        val intent = Intent(context, TtsPlaybackService::class.java)
            .setAction(TtsPlaybackService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun togglePlayPause() = manager.togglePlayPause()
    fun stop() = manager.stop()
    fun next() = manager.skipNext()
    fun previous() = manager.skipPrevious()
    fun consumeError(): String? = manager.consumeError()
}
