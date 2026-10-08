package app.openscout.scout.core.notify

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import app.openscout.scout.MainActivity
import app.openscout.scout.R
import app.openscout.scout.core.ScoutClient
import app.openscout.scout.core.bridge.BridgeConnection
import app.openscout.scout.core.bridge.BridgeEvent
import app.openscout.scout.core.model.InboxItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap

/**
 * Turns the bridge's `operator:notify` pushes into Android notifications while Scout
 * is out of sight. A permission request gets Allow and Deny actions that answer it
 * through `actionDecide` without opening the app; questions and asks open the app.
 *
 * It only hears what arrives on the live link, so it is useful while [ApprovalWatchService]
 * keeps that link open in the background.
 */
class ApprovalNotifier(
    private val context: Context,
    private val connection: BridgeConnection,
    private val client: ScoutClient,
    private val scope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }
    private val manager = NotificationManagerCompat.from(context)

    /** Inbox item ids with a notification up, so a resolved request can be taken down. */
    private val posted = ConcurrentHashMap.newKeySet<String>()

    /** True while no Scout activity is visible; in sight, Home's "Waiting on you" covers it. */
    private val outOfSight: Boolean
        get() = !ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)

    fun start() {
        createChannels()
        scope.launch {
            connection.events.collect { event ->
                if (event !is BridgeEvent.OperatorNotify || !outOfSight) return@collect
                val item = event.item?.let { runCatching { json.decodeFromJsonElement(InboxItem.serializer(), it) }.getOrNull() } ?: return@collect
                post(item, quiet = event.tier == "badge")
            }
        }
    }

    /**
     * After a gap in the link, catch up from `mobile.inbox`: post requests that arrived
     * since [sinceMs] and take down any that were answered somewhere else.
     */
    suspend fun catchUp(sinceMs: Long) {
        val items = runCatching { client.inbox() }.getOrNull() ?: return
        reconcile(items.mapTo(HashSet()) { it.id })
        if (!outOfSight) return
        items.filter { it.createdMs >= sinceMs && it.id !in posted }.forEach { post(it, quiet = it.risk == "low") }
    }

    /** Take down notifications for requests that are no longer open. */
    fun reconcile(openIds: Set<String>) {
        posted.filter { it !in openIds }.forEach(::dismiss)
    }

    fun dismiss(itemId: String) {
        posted.remove(itemId)
        manager.cancel(TAG, itemId.hashCode())
    }

    /** Swap a request's actions for a line saying what went wrong, keeping it tappable. */
    fun showFailure(item: InboxItem, message: String) {
        notify(item, base(item, quiet = true).setContentText(message).setStyle(NotificationCompat.BigTextStyle().bigText(message)))
    }

    private fun post(item: InboxItem, quiet: Boolean) {
        val builder = base(item, quiet)
        if (item.isDecidableApproval) {
            val payload = json.encodeToString(InboxItem.serializer(), item)
            builder.addAction(0, "Deny", ApprovalActionReceiver.intent(context, payload, approve = false))
            builder.addAction(0, "Allow", ApprovalActionReceiver.intent(context, payload, approve = true))
        }
        notify(item, builder)
    }

    private fun base(item: InboxItem, quiet: Boolean): NotificationCompat.Builder {
        val title = item.title.ifBlank { if (item.kind == "question") "Has a question" else "Needs your approval" }
        val body = listOfNotNull(item.description.takeIf { it.isNotBlank() }, item.detail?.takeIf { it.isNotBlank() }).joinToString("\n")
        val open = PendingIntent.getActivity(
            context,
            item.id.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_APPROVALS)
            .setSmallIcon(R.drawable.ic_stat_scout)
            .setContentTitle(title)
            .setContentText(body.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSubText(item.sessionName.takeIf { it.isNotBlank() })
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (quiet) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setSilent(quiet)
            .setOnlyAlertOnce(true)
            .setWhen(item.createdMs.takeIf { it > 0 } ?: System.currentTimeMillis())
            .setShowWhen(true)
            .setAutoCancel(true)
            .setContentIntent(open)
    }

    private fun notify(item: InboxItem, builder: NotificationCompat.Builder) {
        if (!canPost(context)) return
        posted += item.id
        @Suppress("MissingPermission") // checked in canPost
        manager.notify(TAG, item.id.hashCode(), builder.build())
    }

    private fun createChannels() {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_APPROVALS, "Permission requests", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "An agent is waiting for you to allow or deny something."
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WATCH, "Background link", NotificationManager.IMPORTANCE_MIN).apply {
                description = "Shown while Scout keeps its link to your computer open in the background."
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val CHANNEL_APPROVALS = "approvals"
        const val CHANNEL_WATCH = "watch"
        private const val TAG = "approval"

        fun canPost(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }
}
