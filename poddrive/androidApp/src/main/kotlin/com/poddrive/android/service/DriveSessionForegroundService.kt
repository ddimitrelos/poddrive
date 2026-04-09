package com.poddrive.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.poddrive.android.MainActivity
import com.poddrive.android.PodDriveApplication
import com.poddrive.domain.model.Episode

/**
 * Foreground service that keeps audio playback alive while the app is backgrounded (Story 4.10 AC5).
 *
 * Holds the OS-required foreground notification so Android does not kill the audio process when
 * the screen locks or the user switches apps. Also owns the MediaSession lifecycle so that
 * hardware media buttons, lock screen controls, and the system media notification work
 * (Story 4.11 AC4).
 *
 * Start via [startWithEpisode]; stop via [stop].
 *
 * MediaSession is created lazily on first [ACTION_UPDATE_METADATA] command using the ExoPlayer
 * instance exposed by [com.poddrive.audio.AudioPlayer.getExoPlayerForSession].
 *
 * [Source: Story 4.10 AC5; Story 4.11 AC4; AndroidManifest.xml foregroundServiceType="mediaPlayback"]
 */
class DriveSessionForegroundService : Service() {

    private var mediaSession: androidx.media3.session.MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification(episodeTitle = null, podcastName = null))
        initMediaSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_UPDATE_METADATA -> {
                val title = intent.getStringExtra(EXTRA_EPISODE_TITLE)
                val podcast = intent.getStringExtra(EXTRA_PODCAST_NAME)
                updateMediaMetadata(title, podcast)
                updateNotification(title, podcast)
            }
            ACTION_STOP -> {
                stopPlayback()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseMediaSession()
        super.onDestroy()
    }

    // -------------------------------------------------------------------------
    // MediaSession (Story 4.11 AC4)
    // -------------------------------------------------------------------------

    private fun initMediaSession() {
        val podApp = application as? PodDriveApplication ?: return
        @Suppress("UNCHECKED_CAST")
        val player = podApp.audioPlayer.getExoPlayerForSession()
            as? androidx.media3.common.Player ?: run {
            Log.w(TAG, "MediaSession not created: ExoPlayer not yet initialized")
            return
        }

        val sessionActivityIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        mediaSession = androidx.media3.session.MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .build()
        Log.d(TAG, "MediaSession initialized")
    }

    private fun updateMediaMetadata(title: String?, podcastName: String?) {
        val session = mediaSession ?: return
        val metadata = androidx.media3.common.MediaMetadata.Builder()
            .setTitle(title ?: "")
            .setArtist(podcastName ?: "")
            .build()
        val currentItem = session.player.currentMediaItem ?: return
        val updated = currentItem.buildUpon().setMediaMetadata(metadata).build()
        session.player.replaceMediaItem(session.player.currentMediaItemIndex, updated)
    }

    private fun releaseMediaSession() {
        mediaSession?.release()
        mediaSession = null
    }

    // -------------------------------------------------------------------------
    // Notification (Story 4.10 AC5)
    // -------------------------------------------------------------------------

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "PodDrive Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "In-app podcast playback controls"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(episodeTitle: String?, podcastName: String?): android.app.Notification {
        val contentText = when {
            episodeTitle != null && podcastName != null -> "$podcastName · $episodeTitle"
            episodeTitle != null -> episodeTitle
            else -> "Tap to return to PodDrive"
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(episodeTitle ?: "PodDrive")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(title: String?, podcastName: String?) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildNotification(title, podcastName))
    }

    private fun stopPlayback() {
        releaseMediaSession()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        private const val TAG = "DriveSessionForegroundService"
        private const val CHANNEL_ID = "poddrive_playback"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_UPDATE_METADATA = "com.poddrive.ACTION_UPDATE_METADATA"
        const val ACTION_STOP = "com.poddrive.ACTION_STOP"
        const val EXTRA_EPISODE_TITLE = "extra_episode_title"
        const val EXTRA_PODCAST_NAME = "extra_podcast_name"

        /** Starts the service (or updates its notification) with the given episode info. */
        fun startWithEpisode(context: Context, episodeTitle: String, podcastName: String) {
            val intent = Intent(context, DriveSessionForegroundService::class.java).apply {
                action = ACTION_UPDATE_METADATA
                putExtra(EXTRA_EPISODE_TITLE, episodeTitle)
                putExtra(EXTRA_PODCAST_NAME, podcastName)
            }
            context.startForegroundService(intent)
        }

        /** Stops the foreground service and removes the notification. */
        fun stop(context: Context) {
            context.startService(
                Intent(context, DriveSessionForegroundService::class.java).apply {
                    action = ACTION_STOP
                }
            )
        }
    }
}
