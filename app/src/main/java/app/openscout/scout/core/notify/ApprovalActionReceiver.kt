package app.openscout.scout.core.notify

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.openscout.scout.ScoutApplication
import app.openscout.scout.core.model.InboxItem
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

/** Allow / Deny from a permission notification: answers it over the link, no app UI. */
class ApprovalActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as ScoutApplication
        val item = intent.getStringExtra(EXTRA_ITEM)
            ?.let { runCatching { json.decodeFromString(InboxItem.serializer(), it) }.getOrNull() } ?: return
        val approve = intent.getBooleanExtra(EXTRA_APPROVE, false)
        val pending = goAsync()
        app.appScope.launch {
            try {
                // The process may have been restarted since the notification went up.
                if (!app.connection.isConnected) app.connection.connect()
                app.client.decideAction(item, approve)
                app.notifier.dismiss(item.id)
            } catch (e: Exception) {
                app.notifier.showFailure(item, "Couldn't ${if (approve) "allow" else "deny"} it from here. Open Scout to answer.")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_ITEM = "item"
        private const val EXTRA_APPROVE = "approve"
        private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

        fun intent(context: Context, itemJson: String, approve: Boolean): PendingIntent {
            val intent = Intent(context, ApprovalActionReceiver::class.java)
                .setAction(if (approve) "app.openscout.scout.ALLOW" else "app.openscout.scout.DENY")
                .putExtra(EXTRA_ITEM, itemJson)
                .putExtra(EXTRA_APPROVE, approve)
            // Distinct request codes per item and decision so neither action overwrites the other.
            val code = 31 * itemJson.hashCode() + if (approve) 1 else 0
            return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }
    }
}
