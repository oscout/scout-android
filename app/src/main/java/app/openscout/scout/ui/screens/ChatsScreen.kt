package app.openscout.scout.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.core.model.CommsConversation
import app.openscout.scout.ui.AppViewModel
import app.openscout.scout.ui.components.EmptyState
import app.openscout.scout.ui.components.InlineError
import app.openscout.scout.ui.components.ListContentPadding
import app.openscout.scout.ui.components.Monogram
import app.openscout.scout.ui.components.MonoText
import app.openscout.scout.ui.components.OfflineBanner
import app.openscout.scout.ui.components.PrimaryAction
import app.openscout.scout.ui.components.ScoutTopBar
import app.openscout.scout.ui.components.relativeTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(vm: AppViewModel, onOpen: (CommsConversation) -> Unit, onNewSession: () -> Unit) {
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val link by vm.linkState.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf("all") }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { ScoutTopBar("Chats", subtitle = "direct messages and channels") },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = conversations.loading && conversations.data != null,
            onRefresh = vm::refreshConversations,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = ListContentPadding) {
                if (link is LinkState.Failed) item { OfflineBanner((link as LinkState.Failed).message, vm::retryNow) }
                val all = conversations.data
                if (!all.isNullOrEmpty()) {
                    item("filters") {
                        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            listOf("all" to "All", "direct" to "Direct", "channel" to "Channels").forEach { (key, label) ->
                                FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp))
                            }
                        }
                    }
                }
                val shown = all?.filter {
                    when (filter) {
                        "direct" -> it.kind == "direct" || it.kind == "dm"
                        "channel" -> it.kind != "direct" && it.kind != "dm"
                        else -> true
                    }
                }
                when {
                    all == null && conversations.error != null -> item { InlineError(conversations.error!!) }
                    all == null -> item { LoadingLine("Loading conversations…") }
                    all.isEmpty() -> item {
                        EmptyState(
                            Icons.Outlined.ChatBubbleOutline,
                            "No conversations yet",
                            "Message an agent from Agents, or start a session in a project. Conversations stay in sync with your computer.",
                            action = { PrimaryAction("New session", onNewSession) },
                        )
                    }
                    shown.isNullOrEmpty() -> item { QuietLine("Nothing in this filter.") }
                    else -> items(shown, key = { it.id }) { c ->
                        ConversationRow(c) { onOpen(c) }
                        HorizontalDivider(Modifier.padding(start = 76.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(c: CommsConversation, onClick: () -> Unit) {
    val unread = c.unreadCount ?: 0
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val isChannel = c.kind != "direct" && c.kind != "dm"
        Monogram(
            if (isChannel) "#" + c.title.removePrefix("#") else c.title,
            tint = if (isChannel) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (isChannel) "# " else "") + c.title.removePrefix("#").ifBlank { c.id },
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                MonoText(relativeTime(c.lastMessageMs))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = listOfNotNull(c.lastMessageAuthor?.takeIf { it.isNotBlank() }, c.lastMessagePreview?.takeIf { it.isNotBlank() })
                    .joinToString(": ").ifBlank { c.topic ?: "No messages yet" }
                Text(
                    preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box { Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("$unread") } }
                }
            }
        }
    }
}
