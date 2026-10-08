package app.openscout.scout.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import app.openscout.scout.core.model.TailEvent
import app.openscout.scout.ui.AppViewModel
import app.openscout.scout.ui.components.EmptyState
import app.openscout.scout.ui.components.Eyebrow
import app.openscout.scout.ui.components.InlineError
import app.openscout.scout.ui.components.ListContentPadding
import app.openscout.scout.ui.components.MonoText
import app.openscout.scout.ui.components.OfflineBanner
import app.openscout.scout.ui.components.ScoutTopBar
import app.openscout.scout.ui.components.StatusDot
import app.openscout.scout.ui.components.harnessLabel
import app.openscout.scout.ui.components.relativeTime
import app.openscout.scout.ui.theme.Mono
import app.openscout.scout.ui.theme.Scout

/** The fleet tail: a polled slice of observed harness events, refreshed every few seconds while visible. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TailScreen(vm: AppViewModel) {
    DisposableEffect(Unit) {
        vm.setTailVisible(true)
        onDispose { vm.setTailVisible(false) }
    }
    val tail by vm.tail.collectAsStateWithLifecycle()
    val paused by vm.isTailPaused.collectAsStateWithLifecycle()
    val link by vm.linkState.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf("all") }
    var project by rememberSaveable { mutableStateOf<String?>(null) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ScoutTopBar(
                "Tail",
                subtitle = if (paused) "paused" else "live · every 3s",
                actions = {
                    IconButton(onClick = { vm.setTailPaused(!paused) }) {
                        Icon(if (paused) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, if (paused) "Resume" else "Pause")
                    }
                },
            )
        },
    ) { padding ->
        val events = tail.data
        val projects = events?.map { it.project }?.filter { it.isNotBlank() }?.distinct()?.sorted().orEmpty()
        val shown = events?.filter { e ->
            (kind == "all" || when (kind) {
                "assistant" -> e.kind == "assistant"
                "tool" -> e.kind == "tool" || e.kind == "tool-result"
                "user" -> e.kind == "user"
                else -> true
            }) && (project == null || e.project == project) && e.summary.isNotBlank() && !e.summary.startsWith("[file-history")
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = ListContentPadding) {
            if (link is LinkState.Failed) item { OfflineBanner((link as LinkState.Failed).message, vm::retryNow) }
            item("filters") {
                LazyRow(contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp)) {
                    items(listOf("all" to "All", "assistant" to "Replies", "tool" to "Tools", "user" to "Prompts")) { (key, label) ->
                        FilterChip(kind == key, { kind = key }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp))
                    }
                    items(projects) { p ->
                        FilterChip(project == p, { project = if (project == p) null else p }, label = { Text(p) }, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
            when {
                events == null && tail.error != null -> item { InlineError(tail.error!!) }
                events == null -> item { LoadingLine("Listening to your harnesses…") }
                shown.isNullOrEmpty() -> item {
                    EmptyState(Icons.Outlined.GraphicEq, "Quiet", "No harness events match. Tail shows what Claude Code, Codex and other harnesses are doing on your computer.")
                }
                else -> items(shown, key = { it.id }) { e -> TailRow(e) }
            }
        }
    }
}

@Composable
private fun TailRow(e: TailEvent) {
    val color = when (e.kind) {
        "assistant" -> MaterialTheme.colorScheme.primary
        "user" -> MaterialTheme.colorScheme.tertiary
        "tool" -> Scout.tokens.warn
        "tool-result" -> Scout.tokens.warn.copy(alpha = 0.6f)
        "system" -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 7.dp), verticalAlignment = Alignment.Top) {
        StatusDot(color, Modifier.padding(top = 6.dp), size = 6.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Eyebrow(listOfNotNull(harnessLabel(e.source) ?: e.source, e.project.takeIf { it.isNotBlank() }, e.kind).joinToString(" · "), Modifier.weight(1f))
                MonoText(relativeTime(e.tsMs))
            }
            Text(
                e.summary,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = if (e.kind.startsWith("tool")) Mono else MaterialTheme.typography.bodySmall.fontFamily),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
