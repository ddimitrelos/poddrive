package com.poddrive.android.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Foreground service that keeps the PodDrive process alive during an active drive session.
 * Satisfies Android's foreground-service requirement for background audio (FR12, NFR4).
 *
 * Responsibilities:
 * - Post a persistent notification so the OS does not kill the process mid-drive
 * - Provide a [CoroutineScope] for future background work (BT listener, sync triggers)
 *
 * Idempotent: multiple [startService] calls are safe — the service is already running.
 * Call [stopSelf] when session transitions to Ended (wired via DriveSessionViewModel observer).
 *
 * [Source: architecture.md#Audio Boundary]
 * [Stories 3.2, 4.2]
 */
class DriveSessionForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PodDrive Active")
            .setContentText("Drive session in progress")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Drive Session",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active while a PodDrive session is running"
            }
            getSystemService(NotificationManager::class.java)
                ?.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "poddrive_drive_session"
        private const val NOTIFICATION_ID = 1001
    }
}
