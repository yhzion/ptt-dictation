package com.ptt.dictation.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat

class PttForegroundService : Service() {
    companion object {
        const val CHANNEL_ID = "ptt_dictation_channel"
        const val NOTIFICATION_ID = 1
    }

    inner class LocalBinder : Binder() {
        val service: PttForegroundService get() = this@PttForegroundService
    }

    private val binder = LocalBinder()

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Standby"))
    }

    fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(status))
    }

    private fun createNotificationChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                "PTT Dictation",
                NotificationManager.IMPORTANCE_LOW,
            )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(status: String): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PTT Dictation")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .build()
    }
}
