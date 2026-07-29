package io.grimoire.app.data.tts

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import io.grimoire.app.GrimoireApp
import io.grimoire.app.util.AppLocale
import io.grimoire.app.MainActivity
import io.grimoire.app.R

/** Builds the [MediaStyle] notification that hosts the read-aloud transport controls. */
object TtsNotificationBuilder {

    const val NOTIFICATION_ID = 1010

    fun build(
        context: Context,
        token: MediaSessionCompat.Token,
        nowPlaying: TtsNowPlaying?,
        state: TtsPlaybackState,
    ): Notification {
        val playing = state == TtsPlaybackState.PLAYING
        // Resolve strings in the in-app language override, like every other notification.
        val res = AppLocale.wrap(context)
        val builder = NotificationCompat.Builder(context, GrimoireApp.TTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tts)
            .setContentTitle(nowPlaying?.chapterName ?: res.getString(R.string.tts_notification_title))
            .setContentText(nowPlaying?.novelTitle ?: "")
            .setContentIntent(contentIntent(context))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(playing)
            .setShowWhen(false)
            .addAction(
                action(context, android.R.drawable.ic_media_previous, res.getString(R.string.action_previous),
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS),
            )
            .addAction(
                if (playing) {
                    action(context, android.R.drawable.ic_media_pause, res.getString(R.string.action_pause),
                        PlaybackStateCompat.ACTION_PLAY_PAUSE)
                } else {
                    action(context, android.R.drawable.ic_media_play, res.getString(R.string.action_play),
                        PlaybackStateCompat.ACTION_PLAY_PAUSE)
                },
            )
            .addAction(
                action(context, android.R.drawable.ic_media_next, res.getString(R.string.action_next),
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT),
            )
            .addAction(
                action(context, R.drawable.ic_tts_stop, res.getString(R.string.action_stop), PlaybackStateCompat.ACTION_STOP),
            )
            .setStyle(
                MediaStyle()
                    .setMediaSession(token)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(
                            context, PlaybackStateCompat.ACTION_STOP,
                        ),
                    ),
            )
        return builder.build()
    }

    private fun action(context: Context, icon: Int, title: String, mediaAction: Long) =
        NotificationCompat.Action(
            icon, title,
            MediaButtonReceiver.buildMediaButtonPendingIntent(context, mediaAction),
        )

    private fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
