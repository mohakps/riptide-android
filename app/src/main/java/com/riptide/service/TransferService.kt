package com.riptide.service

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import com.riptide.MainActivity
import com.riptide.RiptideApp
import com.riptide.core.ConnectionState
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
        // Held for the whole session, not just a transfer, and with no
        // timeout: the service itself only runs while a session is
        // connected, so its lifetime bounds the lock. onDestroy releases
        // it, and the OS releases it if the process dies.
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "riptide:transfer").apply {
            if (!isHeld) acquire()
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
        // Become a foreground service synchronously, before any async
        // work: a service started with startForegroundService must call
        // startForeground within 5 seconds or the system kills it. The
        // collector below replaces this notification at once with the
        // right one (transfer progress, or connected).
        startForeground(
            TransferNotificationHelper.NOTIFICATION_ID,
            TransferNotificationHelper.buildConnectedNotification(
                this, session.peerName.value.ifBlank { "PC" }, contentIntent, cancelIntent
            )
        )
        job?.cancel()
        job = scope.launch {
            combine(session.connection, session.active, session.peerName) { connection, active, peer ->
                Triple(connection, active, peer)
            }.collect { (connection, active, peer) ->
                // No session, no service. SessionManager also stops the
                // service on disconnect and on every session failure;
                // this is the second half of that pair, for the case
                // where the connection drops without the manager
                // noticing first.
                if (connection != ConnectionState.CONNECTED) {
                    stopSelf()
                    return@collect
                }
                val notification = if (active != null) {
                    TransferNotificationHelper.buildTransferNotification(
                        this@TransferService, active.filename, active.direction,
                        (active.progress * 100).toInt(), active.speedFormatted, contentIntent, cancelIntent
                    )
                } else {
                    TransferNotificationHelper.buildConnectedNotification(
                        this@TransferService, peer.ifBlank { "PC" }, contentIntent, cancelIntent
                    )
                }
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
