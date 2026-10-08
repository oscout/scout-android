package app.openscout.scout.ui.screens

import app.openscout.scout.ui.components.Harness
import app.openscout.scout.ui.components.HarnessMark
import app.openscout.scout.ui.components.Lamp
import app.openscout.scout.ui.components.LampState
import app.openscout.scout.ui.components.LitBox
import app.openscout.scout.ui.components.RowRule
import app.openscout.scout.ui.components.SectionHead
import app.openscout.scout.ui.components.Segmented
import app.openscout.scout.ui.components.Tag
import app.openscout.scout.ui.theme.ScoutType
import androidx.compose.foundation.layout.Arrangement

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.core.model.Agent
import app.openscout.scout.ui.AppViewModel
import app.openscout.scout.ui.components.Chip
import app.openscout.scout.ui.components.EmptyState
import app.openscout.scout.ui.components.Eyebrow
import app.openscout.scout.ui.components.InlineError
import app.openscout.scout.ui.components.ListContentPadding
import app.openscout.scout.ui.components.MonoText
import app.openscout.scout.ui.components.Monogram
import app.openscout.scout.ui.components.OfflineBanner
import app.openscout.scout.ui.components.PrimaryAction
import app.openscout.scout.ui.components.ScoutCard
import app.openscout.scout.ui.components.ScoutTopBar
import app.openscout.scout.ui.components.SecondaryAction
import app.openscout.scout.ui.components.SectionHeader
import app.openscout.scout.ui.components.StatusDot
import app.openscout.scout.ui.components.harnessLabel
import app.openscout.scout.ui.components.relativeTime
import app.openscout.scout.ui.theme.Scout

@Composable
fun livenessColor(agent: Agent): Color = when {
    agent.attention -> Scout.tokens.warn
    agent.liveness == Agent.Liveness.Live -> Scout.tokens.ok
    agent.liveness == Agent.Liveness.Idle -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentsScreen(vm: AppViewModel, onOpen: (Agent) -> Unit, onNewSession: () -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val link by vm.linkState.collectAsStateWithLifecycle()
    var grouping by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ScoutTopBar("Agents", subtitle = agents.data?.let { list -> "${list.count { it.liveness == Agent.Liveness.Live }} live · ${list.size} known" }) },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = agents.loading && agents.data != null,
            onRefresh = vm::refreshAgents,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = ListContentPadding) {
                if (link is LinkState.Failed) item { OfflineBanner((link as LinkState.Failed).message, vm::retryNow) }
                val list = agents.data
                when {
                    list == null && agents.error != null -> item { InlineError(agents.error!!) }
                    list == null -> item { LoadingLine("Reading the agent ledger…") }
                    list.isEmpty() -> item {
                        EmptyState(
                            Icons.Outlined.Groups,
                            "No agents yet",
                            "Agents appear once a session runs in one of your projects. Start one from here — it runs on your computer.",
                            action = { PrimaryAction("New session", onNewSession) },
                        )
                    }
                    else -> {
                        val sorted = list.sortedWith(compareByDescending<Agent> { it.attention }.thenByDescending { it.liveness == Agent.Liveness.Live }.thenByDescending { it.lastActiveMs ?: 0 })
                        val groups: List<Pair<String, List<Agent>>> = if (grouping == 0) {
                            val now = sorted.filter { it.attention || it.liveness == Agent.Liveness.Live }
                            listOf("Now" to now, "Earlier" to (sorted - now.toSet())).filter { it.second.isNotEmpty() }
                        } else {
                            sorted.groupBy { it.projectName ?: "Elsewhere" }.toSortedMap(String.CASE_INSENSITIVE_ORDER).toList()
                        }
                        groups.forEachIndexed { gi, (title, members) ->
                            item("h-$title") {
                                SectionHead(title, count = members.size.toString(), modifier = Modifier.padding(horizontal = 8.dp)) {
                                    if (gi == 0) Segmented(listOf("Recent", "Project"), grouping, { grouping = it })
                                }
                            }
                            item("b-$title") {
                                LitBox(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                    members.forEachIndexed { i, a ->
                                        if (i > 0) RowRule()
                                        AgentRow(a) { onOpen(a) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AgentRow(agent: Agent, onClick: () -> Unit) {
    val c = Scout.colors
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Lamp(
                when {
                    agent.attention -> LampState.Signal
                    agent.liveness == Agent.Liveness.Live -> LampState.Live
                    else -> LampState.Hollow
                },
            )
            Text(
                agent.title,
                style = ScoutType.name.copy(fontSize = ScoutType.prose.fontSize),
                color = if (agent.liveness == Agent.Liveness.Offline) c.second else c.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (agent.attention) Tag("asking", c.signal)
            else Text(relativeTime(agent.lastActiveMs), style = ScoutType.meta, color = c.dim)
        }
        Row(Modifier.padding(start = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HarnessMark(Harness.of(agent.harness))
            Text(
                listOfNotNull(agent.projectName, agent.nodeName).joinToString(" · ").ifBlank { agent.selector ?: agent.id },
                style = ScoutType.mono11, color = c.second, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            )
        }
        val line = agent.pendingAskText ?: agent.statusLabel?.takeIf { it.isNotBlank() && !it.equals(agent.state, true) }
        if (line != null) {
            Text(line, style = ScoutType.bodySmall, color = if (agent.attention) c.body else c.second, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 16.dp, top = 4.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentDetailScreen(vm: AppViewModel, agentId: String, onBack: () -> Unit, onMessage: (Agent) -> Unit) {
    val agents by vm.agents.collectAsStateWithLifecycle()
    val activity by vm.agentActivity.collectAsStateWithLifecycle()
    val agent = agents.data?.firstOrNull { it.id == agentId }
    LaunchedEffect(agentId) { vm.loadAgentActivity(agentId) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ScoutTopBar(agent?.title ?: "Agent", subtitle = agent?.selector ?: agentId, onBack = onBack) },
    ) { padding ->
        if (agent == null) {
            Column(Modifier.padding(padding)) { LoadingLine("Looking up agent…") }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = ListContentPadding) {
            item("card") {
                ScoutCard(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Monogram(agent.title, tint = livenessColor(agent), size = 48.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(agent.title, style = MaterialTheme.typography.titleLarge)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(livenessColor(agent), size = 7.dp)
                                Spacer(Modifier.width(6.dp))
                                Eyebrow(agent.statusLabel ?: agent.state.replace('_', ' '))
                            }
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Fact("Runtime", listOfNotNull(harnessLabel(agent.harness), agent.transport).joinToString(" · ").ifBlank { "unknown" })
                    agent.workspaceRoot?.let { Fact("Project", it.replace(Regex("^/home/[^/]+|^/Users/[^/]+"), "~")) }
                    agent.nodeName?.let { Fact("Host", it) }
                    Fact("Last active", relativeTime(agent.lastActiveMs).ifBlank { "—" })
                    agent.pendingAskText?.let {
                        Spacer(Modifier.height(8.dp))
                        Eyebrow("Waiting on you", color = Scout.tokens.warn)
                        Text(it, style = MaterialTheme.typography.bodyMedium)
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryAction("Message", { onMessage(agent) }, Modifier.fillMaxWidth(), icon = Icons.AutoMirrored.Outlined.Chat)
                    if (agent.liveness == Agent.Liveness.Live) {
                        Spacer(Modifier.height(10.dp))
                        SecondaryAction("Interrupt", { vm.interruptAgentSafely(agent.id) }, Modifier.fillMaxWidth(), icon = Icons.Outlined.StopCircle)
                    }
                }
            }
            item("act-h") { SectionHeader("Recent activity") }
            val acts = activity?.takeIf { it.first == agentId }?.second
            when {
                acts == null || (acts.data == null && acts.loading) -> item { LoadingLine("Loading activity…") }
                acts.error != null && acts.data == null -> item { InlineError(acts.error) }
                acts.data.isNullOrEmpty() -> item { QuietLine("No recent messages from this agent.") }
                else -> items(acts.data, key = { it.id }) { a ->
                    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Row {
                            Eyebrow(a.actorName.ifBlank { a.actorId }, Modifier.weight(1f))
                            MonoText(relativeTime(a.timestampMs))
                        }
                        Text(a.detail ?: a.title, style = MaterialTheme.typography.bodyMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Eyebrow(label, Modifier.width(110.dp))
        MonoText(value, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
    }
}
