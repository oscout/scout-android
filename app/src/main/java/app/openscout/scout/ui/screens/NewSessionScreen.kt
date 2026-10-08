package app.openscout.scout.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.ui.AppViewModel
import app.openscout.scout.ui.NewSessionUi
import app.openscout.scout.ui.components.Eyebrow
import app.openscout.scout.ui.components.InlineError
import app.openscout.scout.ui.components.MonoText
import app.openscout.scout.ui.components.PrimaryAction
import app.openscout.scout.ui.components.ScoutTopBar
import app.openscout.scout.ui.components.SectionHeader
import app.openscout.scout.ui.components.harnessLabel
import app.openscout.scout.ui.theme.Scout

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewSessionScreen(vm: AppViewModel, onBack: () -> Unit, onStarted: (String, String, String) -> Unit) {
    val home by vm.home.collectAsStateWithLifecycle()
    val state by vm.newSession.collectAsStateWithLifecycle()
    val link by vm.linkState.collectAsStateWithLifecycle()
    var workspaceId by rememberSaveable { mutableStateOf<String?>(null) }
    var harness by rememberSaveable { mutableStateOf<String?>(null) }
    var instructions by rememberSaveable { mutableStateOf("") }

    DisposableEffect(Unit) { onDispose { vm.resetNewSession() } }
    LaunchedEffect(state) {
        (state as? NewSessionUi.Started)?.let { onStarted(it.conversationId, it.title, it.agentId) }
    }

    val workspaces = home.data?.workspaces.orEmpty()
    val selected = workspaces.firstOrNull { it.id == workspaceId }
    LaunchedEffect(selected?.id) {
        harness = selected?.let { ws -> ws.defaultHarness?.takeIf { d -> ws.readyHarnesses.any { it.harness == d } } ?: ws.readyHarnesses.firstOrNull()?.harness }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = { ScoutTopBar("New session", subtitle = "runs on your computer", onBack = onBack) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.navigationBarsPadding().imePadding().padding(16.dp)) {
                    (state as? NewSessionUi.Failed)?.let { InlineError(it.message, Modifier.padding(bottom = 8.dp)) }
                    PrimaryAction(
                        if (state is NewSessionUi.Starting) "Starting…" else "Start session",
                        { workspaceId?.let { vm.startSession(it, harness, instructions) } },
                        Modifier.fillMaxWidth(),
                        enabled = workspaceId != null && state !is NewSessionUi.Starting && link is LinkState.Connected,
                        icon = Icons.Outlined.RocketLaunch,
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item { SectionHeader("Project") }
            if (workspaces.isEmpty()) item { QuietLine(if (home.data == null) "Loading projects…" else "No projects discovered on your computer.") }
            items(workspaces, key = { it.id }) { ws ->
                Row(
                    Modifier.fillMaxWidth().clickable { workspaceId = ws.id }.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = ws.id == workspaceId, onClick = { workspaceId = ws.id })
                    Column(Modifier.weight(1f)) {
                        Text(ws.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        MonoText(ws.root?.replace(Regex("^/home/[^/]+|^/Users/[^/]+"), "~") ?: ws.id)
                    }
                }
            }
            if (selected != null) {
                item { SectionHeader("Runtime") }
                item {
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        selected.harnesses.forEach { h ->
                            val ready = h.readinessState == "ready"
                            FilterChip(
                                selected = harness == h.harness,
                                onClick = { harness = h.harness },
                                enabled = ready,
                                label = { Text((harnessLabel(h.harness) ?: h.harness) + if (ready) "" else " · ${h.readinessState ?: "unavailable"}") },
                            )
                        }
                    }
                    selected.harnesses.firstOrNull { it.harness == harness }?.readinessDetail?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                }
                item { SectionHeader("First message (optional)") }
                item {
                    OutlinedTextField(
                        value = instructions,
                        onValueChange = { instructions = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(140.dp),
                        placeholder = { Text("What should the agent work on?") },
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.padding(horizontal = 20.dp)) {
                        Eyebrow("Heads up", color = Scout.tokens.warn)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "This launches a real agent process on your computer.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}
