package app.openscout.scout.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.core.model.ActivityItem
import app.openscout.scout.core.model.Workspace
import app.openscout.scout.ui.AppViewModel
import app.openscout.scout.ui.components.Chip
import app.openscout.scout.ui.components.Eyebrow
import app.openscout.scout.ui.components.HostReadout
import app.openscout.scout.ui.components.InlineError
import app.openscout.scout.ui.components.ListContentPadding
import app.openscout.scout.ui.components.MonoText
import app.openscout.scout.ui.components.OfflineBanner
import app.openscout.scout.ui.components.ScoutCard
import app.openscout.scout.ui.components.ScoutMark
import app.openscout.scout.ui.components.SectionHeader
import app.openscout.scout.ui.components.SignalPanel
import app.openscout.scout.ui.components.StatusDot
import app.openscout.scout.ui.components.harnessLabel
import app.openscout.scout.ui.components.linkColor
import app.openscout.scout.ui.components.machineLabel
import app.openscout.scout.ui.components.relativeTime
import app.openscout.scout.ui.theme.EyebrowStyle
import app.openscout.scout.ui.theme.Scout
import java.net.URI

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: AppViewModel,
    onOpenSettings: () -> Unit,
    onNewSession: () -> Unit,
    onOpenThread: (String?, String, String?) -> Unit,
    onOpenAlerts: () -> Unit,
    onOpenAgents: () -> Unit,
    onOpenChats: () -> Unit,
) {
    val link by vm.linkState.collectAsStateWithLifecycle()
    val machines by vm.machines.collectAsStateWithLifecycle()
    val activeKey by vm.activeKey.collectAsStateWithLifecycle()
    val home by vm.home.collectAsStateWithLifecycle()
    val activity by vm.activity.collectAsStateWithLifecycle()
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val conversations by vm.conversations.collectAsStateWithLifecycle()
    val machine = machines.firstOrNull { it.publicKeyHex.equals(activeKey, true) } ?: machines.firstOrNull()

    Box(Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = home.loading && home.data != null,
            onRefresh = { if (link is LinkState.Connected) vm.refreshAll() else vm.retryNow() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = ListContentPadding) {
                item("masthead") {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ScoutMark(size = 26.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Scout", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold))
                            HostReadout(machine, link)
                        }
                        IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings") }
                    }
                }
                if (link is LinkState.Failed) {
                    item("offline") {
                        OfflineBanner((link as LinkState.Failed).message, onRetry = vm::retryNow)
                    }
                }
                item("signal") {
                    val connected = link as? LinkState.Connected
                    SignalPanel(
                        active = connected != null,
                        accent = linkColor(link),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Eyebrow(if (connected != null) "Link · encrypted" else "Link")
                        Spacer(Modifier.height(8.dp))
                        Text(machineLabel(machine), style = MaterialTheme.typography.headlineSmall)
                        MonoText(
                            when (val s = link) {
                                is LinkState.Connected -> "${s.route.label.lowercase()} · ${runCatching { URI(s.relayUrl).authority }.getOrNull() ?: s.relayUrl}"
                                is LinkState.Connecting -> s.detail.lowercase() + "…"
                                is LinkState.Failed -> "unreachable · retrying"
                                LinkState.Idle -> "idle"
                            },
                        )
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(Modifier.height(12.dp))
                        val data = home.data
                        Row(Modifier.fillMaxWidth()) {
                            Stat("Agents", data?.agents?.size ?: data?.totals?.agents, Modifier.weight(1f).clickable(onClick = onOpenAgents))
                            Stat("Chats", conversations.data?.size, Modifier.weight(1f).clickable(onClick = onOpenChats))
                            Stat("Projects", data?.totals?.workspaces ?: data?.workspaces?.size, Modifier.weight(1f))
                            Stat("Needs you", inbox.data?.size, Modifier.weight(1f).clickable(onClick = onOpenAlerts), highlight = (inbox.data?.size ?: 0) > 0)
                        }
                    }
                }

                val needs = inbox.data.orEmpty()
                if (needs.isNotEmpty()) {
                    item("needs-h") {
                        SectionHeader("Needs you") { TextButton(onClick = onOpenAlerts) { Text("All ${needs.size}") } }
                    }
                    items(needs.take(3), key = { "need-" + it.id }) { item ->
                        InboxCard(vm, item, onOpenThread = { id, title -> onOpenThread(id, title, null) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }

                item("activity-h") { SectionHeader("Activity") }
                val acts = activity.data
                when {
                    acts == null && activity.error != null -> item("act-err") { InlineError(activity.error!!) }
                    acts == null -> item("act-loading") { LoadingLine("Reading the broker…") }
                    acts.isEmpty() -> item("act-empty") {
                        QuietLine("No messages yet. Start a session, or message an agent, and the conversation shows up here.")
                    }
                    else -> items(acts.take(8), key = { "act-" + it.id }) { a ->
                        ActivityRow(a) { onOpenThread(a.conversationId, a.channel ?: a.actorName, null) }
                    }
                }

                item("projects-h") { SectionHeader("Projects") }
                val workspaces = home.data?.workspaces
                when {
                    workspaces == null && home.error != null -> item("ws-err") { InlineError(home.error!!) }
                    workspaces == null -> item("ws-loading") { LoadingLine("Discovering projects…") }
                    workspaces.isEmpty() -> item("ws-empty") { QuietLine("No projects discovered. Run `scout setup --source-root <dir>` on your computer.") }
                    else -> items(workspaces.take(8), key = { "ws-" + it.id }) { ws -> WorkspaceRow(ws) }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onNewSession,
            icon = { Icon(Icons.Filled.Add, null) },
            text = { Text("New session") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun Stat(label: String, value: Int?, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Column(modifier.padding(vertical = 2.dp)) {
        Text(
            value?.toString() ?: "–",
            style = MaterialTheme.typography.titleLarge.copy(fontFamily = app.openscout.scout.ui.theme.Mono),
            color = if (highlight) Scout.tokens.warn else MaterialTheme.colorScheme.onSurface,
        )
        Eyebrow(label)
    }
}

@Composable
fun LoadingLine(text: String) {
    Row(Modifier.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        StatusDot(MaterialTheme.colorScheme.onSurfaceVariant, pulsing = true, size = 6.dp)
        Spacer(Modifier.width(10.dp))
        MonoText(text)
    }
}

@Composable
fun QuietLine(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

@Composable
private fun ActivityRow(item: ActivityItem, onClick: () -> Unit) {
    val dot = when {
        item.kind == "system" -> MaterialTheme.colorScheme.onSurfaceVariant
        item.actorId == "operator" -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    Row(
        Modifier.fillMaxWidth().clickable(enabled = item.conversationId != null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StatusDot(dot, Modifier.padding(top = 7.dp), size = 7.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(item.actorName.ifBlank { item.actorId }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    harnessLabel(item.harness)?.let {
                        Spacer(Modifier.width(8.dp)); Chip(it)
                    }
                }
                Spacer(Modifier.width(8.dp))
                MonoText(relativeTime(item.timestampMs))
            }
            Text(
                item.detail?.takeIf { it.isNotBlank() } ?: item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun WorkspaceRow(ws: Workspace) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(ws.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            ws.branch?.let { Spacer(Modifier.width(8.dp)); MonoText("⎇ $it") }
        }
        MonoText(ws.root?.replace(Regex("^/home/[^/]+|^/Users/[^/]+"), "~") ?: ws.id)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ws.harnesses.sortedBy { if (it.readinessState == "ready") 0 else 1 }.forEach { h ->
                Chip(
                    harnessLabel(h.harness) ?: h.harness,
                    color = if (h.readinessState == "ready") Scout.tokens.ok else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }
        }
    }
}
