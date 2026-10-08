package app.openscout.scout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import app.openscout.scout.ui.components.LitBox
import app.openscout.scout.ui.components.RowRule
import app.openscout.scout.ui.components.SectionHead
import app.openscout.scout.ui.components.Segmented
import app.openscout.scout.ui.components.Tag
import app.openscout.scout.ui.theme.Scout
import app.openscout.scout.ui.theme.ScoutType

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
        topBar = { ScoutTopBar("Chats") },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = conversations.loading && conversations.data != null,
            onRefresh = vm::refreshConversations,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = ListContentPadding) {
                if (link is LinkState.Failed) item { OfflineBanner((link as LinkState.Failed).message, vm::retryNow) }
                val all = conversations.data
                val keys = listOf("all", "direct", "channel")
                val shown = all?.filter {
                    when (filter) {
                        "direct" -> it.kind == "direct" || it.kind == "dm"
                        "channel" -> it.kind != "direct" && it.kind != "dm"
                        else -> true
                    }
                }
                item("head") {
                    SectionHead("Conversations", count = shown?.size?.toString(), modifier = Modifier.padding(horizontal = 8.dp)) {
                        Segmented(listOf("All", "Direct", "Channels"), keys.indexOf(filter).coerceAtLeast(0), { filter = keys[it] })
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
                    else -> item("list") {
                        LitBox(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                            shown.forEachIndexed { i, conv ->
                                if (i > 0) RowRule()
                                ConversationRow(conv) { onOpen(conv) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(conv: CommsConversation, onClick: () -> Unit) {
    val c = Scout.colors
    val unread = conv.unreadCount ?: 0
    val isChannel = conv.kind != "direct" && conv.kind != "dm"
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (isChannel) "#" else "@", style = ScoutType.mono11, color = c.dim, modifier = Modifier.width(12.dp))
            Text(
                conv.title.removePrefix("#").ifBlank { conv.id },
                style = ScoutType.name.copy(fontSize = ScoutType.prose.fontSize, fontWeight = if (unread > 0) FontWeight.SemiBold else FontWeight.Medium),
                color = c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (unread > 0) Tag(unread.toString(), c.signal)
            Text(relativeTime(conv.lastMessageMs), style = ScoutType.meta, color = c.dim)
        }
        val preview = listOfNotNull(conv.lastMessageAuthor?.takeIf { it.isNotBlank() }, conv.lastMessagePreview?.takeIf { it.isNotBlank() })
            .joinToString(": ").ifBlank { conv.topic ?: "No messages yet" }
        Text(preview, style = ScoutType.bodySmall, color = if (unread > 0) c.body else c.second, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 20.dp, top = 4.dp))
    }
}
