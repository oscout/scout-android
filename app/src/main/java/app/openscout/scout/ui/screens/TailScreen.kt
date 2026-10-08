package app.openscout.scout.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.core.model.TailEvent
import app.openscout.scout.ui.AppViewModel
import app.openscout.scout.ui.components.EmptyState
import app.openscout.scout.ui.components.Fill
import app.openscout.scout.ui.components.Hairline
import app.openscout.scout.ui.components.Harness
import app.openscout.scout.ui.components.HarnessMark
import app.openscout.scout.ui.components.InlineError
import app.openscout.scout.ui.components.Lamp
import app.openscout.scout.ui.components.LampState
import app.openscout.scout.ui.components.Masthead
import app.openscout.scout.ui.components.OfflineBanner
import app.openscout.scout.ui.components.SectionHead
import app.openscout.scout.ui.components.Segmented
import app.openscout.scout.ui.theme.Scout
import app.openscout.scout.ui.theme.ScoutColors
import app.openscout.scout.ui.theme.ScoutType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What a tail line did, told apart from the tool name that starts a tool line (ios-home-dense round II). */
enum class TailKind(val label: String) { Edit("edit"), Shell("bash"), Read("read"), Tool("tool"), Out("out"), Say("say"), You("you") }

fun TailEvent.tailKind(): TailKind = when (kind) {
    "user" -> TailKind.You
    "assistant" -> TailKind.Say
    "tool-result" -> TailKind.Out
    "tool" -> {
        val name = summary.trimStart().substringBefore(' ').substringBefore('(').lowercase()
        when (name) {
            "edit", "write", "multiedit", "apply_patch", "notebookedit" -> TailKind.Edit
            "bash", "exec", "shell", "exec_command" -> TailKind.Shell
            "read", "grep", "glob", "ls", "webfetch", "websearch" -> TailKind.Read
            "task", "agent", "todowrite", "toolsearch", "skill", "update_plan", "view_image" -> TailKind.Tool
            // Claude and Codex summarize a shell call as the bare command line.
            else -> if (name.startsWith("mcp__") || name.contains("__")) TailKind.Tool else TailKind.Shell
        }
    }
    else -> TailKind.Out
}

fun TailKind.color(c: ScoutColors): Color = when (this) {
    TailKind.Edit -> c.kindEdit
    TailKind.Shell -> c.kindShell
    TailKind.Read -> c.kindRead
    TailKind.Tool -> c.kindTool
    TailKind.Out -> c.kindOut
    TailKind.Say -> c.kindSay
    TailKind.You -> c.kindYou
}

/** Lines that carry no words worth a row: `[attachment]`, `[assistant]`, `[file-history…]`. */
private fun TailEvent.isPlaceholder(): Boolean {
    val s = summary.trim()
    return s.isEmpty() || (s.startsWith("[") && s.endsWith("]") && ' ' !in s) || s.startsWith("[file-history")
}

private val clock = SimpleDateFormat("HH:mm", Locale.getDefault())

private val filters = listOf("All" to null, "Edit" to TailKind.Edit, "Bash" to TailKind.Shell, "Read" to TailKind.Read, "Say" to TailKind.Say, "You" to TailKind.You)

/** Ops · Tail: the fleet firehose, edge to edge in mono, polled every 3s while it's on screen. */
@Composable
fun TailScreen(vm: AppViewModel) {
    DisposableEffect(Unit) {
        vm.setTailVisible(true)
        onDispose { vm.setTailVisible(false) }
    }
    val c = Scout.colors
    val tail by vm.tail.collectAsStateWithLifecycle()
    val paused by vm.isTailPaused.collectAsStateWithLifecycle()
    val link by vm.linkState.collectAsStateWithLifecycle()
    var filter by rememberSaveable { mutableStateOf(0) }
    var onlySession by rememberSaveable { mutableStateOf<String?>(null) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }

    val events = tail.data
    val kind = filters[filter].second
    val shown = events?.filter { e ->
        !e.isPlaceholder() && (kind == null || e.tailKind() == kind) && (onlySession == null || e.sessionId == onlySession)
    }
    val perMinute = events?.let { list ->
        val now = System.currentTimeMillis()
        list.count { now - it.tsMs < 5 * 60_000 } / 5f
    }

    Column(Modifier.fillMaxSize()) {
        Masthead("Ops") {
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable(role = Role.Switch) { vm.setTailPaused(!paused) }
                    .defaultMinSize(minHeight = 48.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Lamp(if (paused) LampState.Hollow else LampState.Live)
                Text(if (paused) "paused" else "following", style = ScoutType.meta, color = if (paused) c.dim else c.second)
            }
        }
        if (link is LinkState.Failed) OfflineBanner((link as LinkState.Failed).message, vm::retryNow)

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item("head") {
                SectionHead("Tail", count = perMinute?.let { "%.1f/min".format(it) }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Segmented(filters.map { it.first }, filter, { filter = it })
                }
            }
            onlySession?.let { sid ->
                item("only") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("only …${sid.takeLast(4)}", style = ScoutType.meta, color = c.second)
                        Fill()
                        Text(
                            "show all",
                            style = ScoutType.meta,
                            color = c.ink,
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onlySession = null }.defaultMinSize(minHeight = 40.dp).padding(horizontal = 8.dp, vertical = 12.dp),
                        )
                    }
                }
            }
            when {
                events == null && tail.error != null -> item { InlineError(tail.error!!) }
                events == null -> item { LoadingLine("Listening to your harnesses…") }
                shown.isNullOrEmpty() -> item {
                    EmptyState(Icons.Outlined.GraphicEq, "Quiet", "Nothing matches. Tail shows what Claude Code, Codex and the other harnesses are doing on your computer.")
                }
                else -> {
                    item("top") { Box(Modifier.fillMaxWidth().height(Hairline).background(c.line)) }
                    items(shown, key = { it.id }) { e ->
                        TailLine(e, isOpen = open == e.id, onClick = { open = if (open == e.id) null else e.id })
                        if (open == e.id) {
                            SessionPop(e, onOnly = { onlySession = e.sessionId; open = null })
                        }
                    }
                    item("bottom") {
                        Box(Modifier.fillMaxWidth().height(Hairline).background(c.line))
                        Spacer(Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }
}

@Composable
private fun TailLine(e: TailEvent, isOpen: Boolean, onClick: () -> Unit) {
    val c = Scout.colors
    val k = e.tailKind()
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (isOpen) Modifier.background(c.boxEdgeHighlight.copy(alpha = if (c.isDark) 0.05f else 0.6f)) else Modifier)
            .clickable(onClick = onClick)
            .height(24.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(clock.format(Date(e.tsMs)), style = ScoutType.small, color = c.dim, modifier = Modifier.width(36.dp), maxLines = 1)
        Row(Modifier.width(48.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            HarnessMark(Harness.of(e.source), size = 9.dp)
            Text(e.sessionId.takeLast(4), style = ScoutType.small, color = c.second, maxLines = 1)
        }
        Text(k.label, style = ScoutType.small.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold), color = k.color(c), modifier = Modifier.width(32.dp), maxLines = 1)
        Text(
            e.summary.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().removePrefix("res: ").replace(Regex("^\\[[a-z-]+\\]\\s*"), ""),
            style = if (k == TailKind.You) ScoutType.bodySmall.copy(fontSize = ScoutType.mono11.fontSize) else ScoutType.mono11,
            color = when (k) {
                TailKind.Out -> c.dim
                TailKind.You -> c.ink
                else -> c.body
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SessionPop(e: TailEvent, onOnly: () -> Unit) {
    val c = Scout.colors
    val shape = RoundedCornerShape(10.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 10.dp, end = 10.dp, top = 3.dp, bottom = 7.dp)
            .clip(shape)
            .background(Brush.verticalGradient(listOf(c.popTop, c.popBottom)))
            .border(Hairline, c.line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            HarnessMark(Harness.of(e.source))
            Text("${e.source.ifBlank { "harness" }} session", style = ScoutType.nameSmall, color = c.ink)
            Text("…${e.sessionId.takeLast(4)}", style = ScoutType.meta, color = c.dim)
            Fill()
            Text(e.harness.takeIf { it != "unattributed" }.orEmpty(), style = ScoutType.meta, color = c.dim)
        }
        Text(
            listOf(e.project, e.cwd).filter { it.isNotBlank() }.distinct().joinToString(" · "),
            style = ScoutType.mono11, color = c.second, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 5.dp),
        )
        Text(e.summary.trim(), style = ScoutType.mono11, color = c.body, maxLines = 6, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(Hairline).background(c.rule))
        Text(
            "Only this session",
            style = ScoutType.bodySmall,
            color = c.ink,
            modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOnly).defaultMinSize(minHeight = 40.dp).padding(vertical = 11.dp),
        )
    }
}
