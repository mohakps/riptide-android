package com.riptide.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.riptide.MainActivity
import com.riptide.RiptideApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TransferService : Service() {
    private var wakeLock: PowerManager.WakeLock? = null
    private var job: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TransferNotificationHelper.ACTION_CANCEL) {
            (application as RiptideApp).session.disconnect()
            stopSelf()
            return START_NOT_STICKY
        }
        TransferNotificationHelper.createNotificationChannel(this)
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "riptide:transfer").apply {
            if (!isHeld) acquire(10 * 60 * 1000L)
        }
        val session = (application as RiptideApp).session
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = PendingIntent.getService(
            this, 1, Intent(this, TransferService::class.java).setAction(TransferNotificationHelper.ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        job?.cancel()
        job = scope.launch {
            combine(session.active, session.peerName) { active, _ -> active }.collect { active ->
                if (active == null) {
                    stopSelf()
                    return@collect
                }
                val notification = TransferNotificationHelper.buildTransferNotification(
                    this@TransferService, active.filename, active.direction,
                    (active.progress * 100).toInt(), active.speedFormatted, contentIntent, cancelIntent
                )
                startForeground(TransferNotificationHelper.NOTIFICATION_ID, notification)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job?.cancel()
        try { if (wakeLock?.isHeld == true) wakeLock?.release() } catch (_: Exception) {}
        super.onDestroy()
    }
}
