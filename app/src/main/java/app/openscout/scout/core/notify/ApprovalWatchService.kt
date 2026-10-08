package app.openscout.scout.core.notify

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.openscout.scout.MainActivity
import app.openscout.scout.R
import app.openscout.scout.ScoutApplication
import app.openscout.scout.core.bridge.BridgeException
import app.openscout.scout.core.bridge.FailureKind
import app.openscout.scout.core.bridge.LinkState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the encrypted link to the paired computer open while Scout is in the
 * background, so [ApprovalNotifier] hears permission requests as they happen.
 * Opt-in from Settings; Android shows its own ongoing notification while it runs.
 */
class ApprovalWatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as ScoutApplication
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, ONGOING_ID, ongoing(), type)
        if (loop?.isActive != true) loop = scope.launch { keepLinked(app) }
        return START_STICKY
    }

    /** Reconnect with backoff whenever the link drops; after each reconnect, catch up on missed requests. */
    private suspend fun keepLinked(app: ScoutApplication) {
        val backoff = listOf(1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L, 60_000L)
        var attempt = 0
        var linkedSince = System.currentTimeMillis()
        while (scope.isActive) {
            val link = app.connection
            if (link.isConnected || link.state.value is LinkState.Connecting) {
                attempt = 0
                delay(5_000)
                continue
            }
            try {
                val lostAt = linkedSince
                link.connect()
                linkedSince = System.currentTimeMillis()
                app.notifier.catchUp(sinceMs = lostAt)
                attempt = 0
            } catch (e: BridgeException) {
                if (e.kind == FailureKind.NotPaired || e.kind == FailureKind.Untrusted) {
                    stopSelf(); return
                }
                delay(backoff[attempt.coerceAtMost(backoff.lastIndex)]); attempt++
            } catch (e: Exception) {
                delay(backoff[attempt.coerceAtMost(backoff.lastIndex)]); attempt++
            }
        }
    }

    private fun ongoing() = NotificationCompat.Builder(this, ApprovalNotifier.CHANNEL_WATCH)
        .setSmallIcon(R.drawable.ic_stat_scout)
        .setContentTitle("Listening for permission requests")
        .setContentText("Scout keeps its link to your computer open while it's in the background.")
        .setOngoing(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_MIN)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(
            PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE),
        )
        .build()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ONGOING_ID = 7001

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, ApprovalWatchService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ApprovalWatchService::class.java))
        }
    }
}
