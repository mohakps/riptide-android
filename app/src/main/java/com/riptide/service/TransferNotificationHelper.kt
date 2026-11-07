package com.riptide.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.riptide.ui.model.TransferDirection

object TransferNotificationHelper {
    const val CHANNEL_ID = "riptide_transfers"
    const val NOTIFICATION_ID = 50001
    const val ACTION_CANCEL = "com.riptide.action.CANCEL_TRANSFER"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Riptide File Transfers", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows ongoing peer-to-peer file transfer progress."
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    fun buildTransferNotification(
        context: Context,
        filename: String,
        direction: TransferDirection,
        progressPercent: Int,
        speedFormatted: String,
        contentIntent: PendingIntent,
        cancelIntent: PendingIntent
    ): Notification {
        val title = if (direction == TransferDirection.SEND) "Sending $filename" else "Receiving $filename"
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText("$progressPercent% · $speedFormatted")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progressPercent, false)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()
    }

    /** The persistent notification while a session is connected but no
     * file is moving. This is what keeps the process alive in the
     * background: the service is a foreground service from the moment
     * the session connects, not only during a transfer. */
    fun buildConnectedNotification(
        context: Context,
        peerName: String,
        contentIntent: PendingIntent,
        disconnectIntent: PendingIntent
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Connected to $peerName")
            .setContentText("Riptide session is active")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Disconnect", disconnectIntent)
            .build()
    }
}
