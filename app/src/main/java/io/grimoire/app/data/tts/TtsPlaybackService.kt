package io.grimoire.app.data.tts

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Thin foreground host for read-aloud playback. All playback logic lives in
 * [TtsPlaybackManager]; this service exists to keep the process alive in the
 * background, own the media-style notification, and route media-button intents.
 */
@AndroidEntryPoint
class TtsPlaybackService : Service() {

    @Inject lateinit var manager: TtsPlaybackManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onBind(intent: Intent?) = null

    private var observing = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Promote to foreground promptly to satisfy the foreground-service start window.
        startForegroundCompat()
        if (intent?.action == ACTION_START) {
            manager.startPlayback()
        } else {
            manager.handleMediaButton(intent)
        }
        if (!observing) {
            observing = true
            scope.launch {
                combine(manager.state, manager.nowPlaying) { state, _ -> state }
                    .collectLatest { state ->
                        applyState(state)
                        if (state == TtsPlaybackState.PAUSED) {
                            delay(PAUSED_IDLE_TIMEOUT_MS)
                            if (manager.state.value == TtsPlaybackState.PAUSED) manager.stop()
                        }
                    }
            }
        }
        return START_NOT_STICKY
    }

    private fun applyState(state: TtsPlaybackState) {
        when (state) {
            TtsPlaybackState.IDLE, TtsPlaybackState.ERROR -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            // Keep media buttons available during the bounded pause window.
            TtsPlaybackState.PLAYING, TtsPlaybackState.LOADING, TtsPlaybackState.PAUSED ->
                startForegroundCompat()
        }
    }

    private fun startForegroundCompat() {
        val notification = manager.buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                TtsNotificationBuilder.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(TtsNotificationBuilder.NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        manager.release()
        super.onDestroy()
    }

    companion object {
        private const val PAUSED_IDLE_TIMEOUT_MS = 5 * 60 * 1000L
        const val ACTION_START = "io.grimoire.app.tts.START"
    }
}
