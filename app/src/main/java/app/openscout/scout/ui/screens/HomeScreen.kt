package app.openscout.scout.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.core.model.FleetActivity
import app.openscout.scout.core.model.Heartrate
import app.openscout.scout.core.model.InboxItem
import app.openscout.scout.core.model.ServiceBudget
import app.openscout.scout.core.model.TailEvent
import app.openscout.scout.ui.AppViewModel
import app.openscout.scout.ui.components.ButtonTone
import app.openscout.scout.ui.components.DotMeter
import app.openscout.scout.ui.components.Fill
import app.openscout.scout.ui.components.Harness
import app.openscout.scout.ui.components.HarnessMark
import app.openscout.scout.ui.components.HostPill
import app.openscout.scout.ui.components.Lamp
import app.openscout.scout.ui.components.LampState
import app.openscout.scout.ui.components.LitBox
import app.openscout.scout.ui.components.LocalOpenDrawer
import app.openscout.scout.ui.components.Masthead
import app.openscout.scout.ui.components.OfflineBanner
import app.openscout.scout.ui.components.RowRule
import app.openscout.scout.ui.components.ScoutButton
import app.openscout.scout.ui.components.SectionHead
import app.openscout.scout.ui.components.Segmented
import app.openscout.scout.ui.components.Tag
import app.openscout.scout.ui.components.relativeTime
import app.openscout.scout.ui.theme.Scout
import app.openscout.scout.ui.theme.ScoutType
import kotlin.math.ceil

private val windows = listOf("30m" to 30 * 60_000L, "4h" to 4 * 3_600_000L, "24h" to 24 * 3_600_000L)

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
    onOpenTail: () -> Unit = {},
) {
    val link by vm.linkState.collectAsStateWithLifecycle()
    val machines by vm.machines.collectAsStateWithLifecycle()
    val activeKey by vm.activeKey.collectAsStateWithLifecycle()
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val heartrate by vm.heartrate.collectAsStateWithLifecycle()
    val fleet by vm.fleet.collectAsStateWithLifecycle()
    val replies by vm.replies.collectAsStateWithLifecycle()
    val agents by vm.agents.collectAsStateWithLifecycle()
    val busy by vm.inboxBusy.collectAsStateWithLifecycle()
    val machine = machines.firstOrNull { it.publicKeyHex.equals(activeKey, true) } ?: machines.firstOrNull()
    val openDrawer = LocalOpenDrawer.current
    var window by rememberSaveable { mutableIntStateOf(0) }

    val now = System.currentTimeMillis()
    val moving = remember(replies.data, window) { movingSessions(replies.data.orEmpty(), now, windows[window].second, cap = 8) }
    val moving30 = remember(replies.data) { movingSessions(replies.data.orEmpty(), now, windows[0].second, cap = 99).size }
    val live = agents.data?.count { it.state == "working" } ?: moving.count { now - it.tsMs < 5 * 60_000 }
    val asks = inbox.data.orEmpty().filter { it.isDecidableApproval || it.isAnswerableQuestion || it.isConversationAsk || it.isTerminalPrompt }
    val decidable = asks.firstOrNull { it.isDecidableApproval }
    val coordination = fleet.data?.activity.orEmpty().sortedByDescending { it.tsMs }.take(8)

    Column(Modifier.fillMaxSize()) {
        Masthead("Home") {
            HostPill(machine, link, onClick = { openDrawer?.invoke() }, extraHosts = (machines.size - 1).coerceAtLeast(0))
            Spacer(Modifier.width(4.dp))
        }
        if (link is LinkState.Failed) OfflineBanner((link as LinkState.Failed).message, onRetry = vm::retryNow)

        PullToRefreshBox(
            isRefreshing = budgets.loading && budgets.data != null,
            onRefresh = { if (link is LinkState.Connected) vm.refreshAll() else vm.retryNow() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp)) {
                item("fleet-head") { SectionHead("Fleet") { Text("now", style = ScoutType.meta, color = Scout.colors.dim) } }
                item("fleet") {
                    FleetRail(
                        budgets = budgets.data?.budgets.orEmpty(),
                        heartrate = heartrate.data,
                        asks = asks,
                        live = live,
                        moving30 = moving30,
                        onOpenAlerts = onOpenAlerts,
                    )
                }

                if (decidable != null) {
                    item("asks-head") {
                        SectionHead("Waiting on you", count = asks.size.toString(), countColor = Scout.colors.signal) {
                            Text(relativeTime(decidable.createdMs, now), style = ScoutType.meta, color = Scout.colors.dim)
                        }
                    }
                    item("ask-${decidable.id}") {
                        AskBox(
                            item = decidable,
                            busy = decidable.id in busy,
                            error = busy[decidable.id],
                            onOpen = onOpenAlerts,
                            onDecide = { approve -> vm.decide(decidable, approve) },
                        )
                    }
                }

                item("moving-head") {
                    SectionHead("Moving", count = moving.size.toString()) {
                        Segmented(windows.map { it.first }, window, { window = it })
                    }
                }
                item("moving") {
                    LitBox(Modifier.fillMaxWidth()) {
                        if (moving.isEmpty()) {
                            Quiet(if (replies.loading && replies.data == null) "Reading the fleet…" else "Nothing moved in the last ${windows[window].first}.")
                        }
                        moving.forEachIndexed { i, event ->
                            if (i > 0) RowRule()
                            MovingRow(event, now) { onOpenTail() }
                        }
                    }
                }

                item("streams-head") {
                    SectionHead("Streams") {
                        Segmented(listOf("Coordination", "Tail"), 0, { if (it == 1) onOpenTail() })
                    }
                }
                item("streams") {
                    LitBox(Modifier.fillMaxWidth()) {
                        if (coordination.isEmpty()) {
                            Quiet(if (fleet.loading && fleet.data == null) "Reading coordination…" else "No coordination yet. Messages and asks between agents land here.")
                        }
                        coordination.forEachIndexed { i, line ->
                            if (i > 0) RowRule()
                            CoordinationRow(line, now) { onOpenThread(line.conversationId, line.actorName ?: "Conversation", line.agentId) }
                        }
                    }
                }
                item("bottom") { Spacer(Modifier.navigationBarsPadding()) }
            }
        }
    }
}

// -- Moving: iOS HomeMovingSessions, on the replies tail --------------------------

/** Newest line per `source:sessionId` inside the window, newest first; Scout-started sessions left out. */
internal fun movingSessions(tail: List<TailEvent>, nowMs: Long, windowMs: Long, cap: Int): List<TailEvent> {
    val skew = 2 * 60_000L
    return tail
        .filter { it.harness != "scout-managed" && it.sessionId.isNotEmpty() && it.tsMs >= nowMs - windowMs && it.tsMs <= nowMs + skew }
        .groupBy { "${it.source}:${it.sessionId}" }
        .map { (_, events) -> events.maxBy { it.tsMs } }
        .sortedByDescending { it.tsMs }
        .take(cap)
}

private fun harnessOf(raw: String?): Harness = Harness.of(raw)

/** The reply's first real line, without the transcript's `[thinking]`-style tag. */
internal fun replyText(summary: String): String =
    summary.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty().trim().replace(Regex("^\\[[a-z-]+\\]\\s*"), "")

// -- Fleet rail ------------------------------------------------------------------

@Composable
private fun FleetRail(
    budgets: List<ServiceBudget>,
    heartrate: Heartrate?,
    asks: List<InboxItem>,
    live: Int,
    moving30: Int,
    onOpenAlerts: () -> Unit,
) {
    val listState = rememberLazyListState()
    val page by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cardWidth = maxWidth - 24.dp
        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(listState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            itemsIndexed(listOf("usage", "activity", "asks")) { index, key ->
                Box(Modifier.width(cardWidth).height(118.dp)) {
                    LitBox(Modifier.fillMaxSize()) {
                        when (key) {
                            "usage" -> UsageCard(budgets, live, moving30)
                            "activity" -> ActivityCard(heartrate)
                            else -> AsksCard(asks, onOpenAlerts)
                        }
                    }
                    PageTicks(page, index, Modifier.align(Alignment.TopCenter))
                }
            }
        }
    }
}

@Composable
private fun PageTicks(page: Int, index: Int, modifier: Modifier) {
    val c = Scout.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(3) { i ->
            val on = i == index
            Box(Modifier.width(if (on) 16.dp else 10.dp).height(1.5.dp).background(if (on && page == index) c.ink else if (on) c.second else c.dotOff))
        }
    }
}

@Composable
private fun CardHead(title: String, trailing: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(30.dp).padding(start = 12.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = ScoutType.label, color = Scout.colors.ink)
        Fill()
        trailing()
    }
}

private data class QuotaLine(
    val harness: Harness,
    val name: String,
    val shortLabel: String?,
    val short: Int?,
    val longLabel: String?,
    val long: Int?,
    val reset: String?,
    val soon: Boolean,
)

/** The bridge sends each provider's windows short-first (e.g. 5h, then wk); read them in that order. */
private fun quotaLines(budgets: List<ServiceBudget>, now: Long): List<QuotaLine> = budgets
    .filter { it.windows.isNotEmpty() }
    .sortedWith(compareBy({ Harness.of(it.provider) == Harness.Other }, { -(it.windows.maxOfOrNull { w -> w.usedPercent } ?: 0.0) }))
    .take(3)
    .map { b ->
        val short = b.windows.getOrNull(0)
        val long = b.windows.getOrNull(1)
        val driving = listOfNotNull(short, long).maxByOrNull { it.usedPercent }
        val soon = driving?.resetAt?.let { it - now in 0..86_400_000L } ?: false
        QuotaLine(
            harness = Harness.of(b.provider),
            name = b.label.ifBlank { b.provider }.lowercase(),
            shortLabel = short?.label,
            short = short?.usedPercent?.toInt(),
            longLabel = long?.label,
            long = long?.usedPercent?.toInt(),
            reset = driving?.reset?.takeIf { it.isNotBlank() },
            soon = soon,
        )
    }

private val quotaColumns = listOf(72.dp, 92.dp, 92.dp)

@Composable
private fun UsageCard(budgets: List<ServiceBudget>, live: Int, moving30: Int) {
    val c = Scout.colors
    val lines = quotaLines(budgets, System.currentTimeMillis())
    Box(Modifier.fillMaxSize()) {
        Column {
            Row(Modifier.fillMaxWidth().height(30.dp).padding(start = 12.dp, end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("USAGE", style = ScoutType.label, color = c.ink, modifier = Modifier.width(quotaColumns[0]))
                Text(lines.firstNotNullOfOrNull { it.shortLabel }?.uppercase().orEmpty(), style = ScoutType.micro, color = c.dim, modifier = Modifier.width(quotaColumns[1]))
                Text(lines.firstNotNullOfOrNull { it.longLabel }?.uppercase().orEmpty(), style = ScoutType.micro, color = c.dim, modifier = Modifier.width(quotaColumns[2]))
                Text("RESETS", style = ScoutType.micro, color = c.dim, textAlign = TextAlign.End, modifier = Modifier.weight(1f))
            }
            if (lines.isEmpty()) {
                Text("No subscription windows reported.", style = ScoutType.bodySmall, color = c.dim, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
            lines.take(2).forEach { QuotaRow(it) }
        }
        Column(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
            RowRule()
            Row(Modifier.height(28.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Lamp(if (live > 0) LampState.Live else LampState.Hollow)
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append(live.toString()) }
                        append(" live · ")
                        withStyle(SpanStyle(color = c.ink, fontWeight = FontWeight.SemiBold)) { append(moving30.toString()) }
                        append(" moving 30m")
                    },
                    style = ScoutType.meta, color = c.dim,
                )
            }
        }
    }
}

@Composable
private fun QuotaRow(q: QuotaLine) {
    val c = Scout.colors
    Row(Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.width(quotaColumns[0]), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            HarnessMark(q.harness)
            Text(q.name, style = ScoutType.nameSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        QuotaCell(q.short, Modifier.width(quotaColumns[1]))
        QuotaCell(q.long, Modifier.width(quotaColumns[2]))
        Text(q.reset ?: "—", style = ScoutType.small, color = if (q.soon) c.signal else c.dim, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuotaCell(percent: Int?, modifier: Modifier) {
    val c = Scout.colors
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        DotMeter(percent)
        Text(
            percent?.let { "$it%" } ?: "—",
            style = ScoutType.small,
            color = if (percent != null && percent > 80) c.signal else c.second,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun spanLabel(ms: Long): String {
    val hours = ms / 3_600_000L
    return if (hours >= 48) "${(hours + 12) / 24}d" else "${hours.coerceAtLeast(1)}h"
}

/** The heartrate window as up to 28 dot columns, up to five dots tall; the newest column in the accent. */
@Composable
private fun ActivityCard(heartrate: Heartrate?) {
    val c = Scout.colors
    val buckets = heartrate?.buckets.orEmpty()
    val columns = remember(buckets) {
        val per = (buckets.size / 28).coerceAtLeast(1)
        buckets.chunked(per).takeLast(28).map { chunk -> ceil((chunk.maxOfOrNull { it.value } ?: 0.0) * 5).toInt().coerceIn(0, 5) }
    }
    val events = buckets.sumOf { it.count }
    val window = heartrate?.windowLabel?.removePrefix("trailing")?.trim().orEmpty()
    // Axis ticks from the buckets' own span, so a different window still labels true.
    val ticks = remember(buckets) {
        val span = buckets.firstOrNull()?.let { System.currentTimeMillis() - it.ts }?.takeIf { it > 0 }
        if (span == null) emptyList() else listOf(1.0, 2.0 / 3, 1.0 / 3).map { spanLabel((span * it).toLong()) } + "now"
    }
    Column(Modifier.fillMaxSize()) {
        CardHead("Activity") { Text(if (window.isEmpty()) "%,d events".format(events) else "%,d events · %s".format(events, window), style = ScoutType.meta, color = c.dim) }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            val last = columns.lastIndex
            columns.forEachIndexed { col, v ->
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (level in 5 downTo 1) {
                        val on = level <= v
                        val now = on && col == last
                        Box(Modifier.size(4.dp).clip(CircleShape).background(if (now) c.life else if (on) c.dotOn.copy(alpha = 0.75f) else c.dotOff))
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ticks.forEach { Text(it, style = ScoutType.micro, color = c.dim) }
        }
    }
}

@Composable
private fun AsksCard(asks: List<InboxItem>, onOpenAlerts: () -> Unit) {
    val c = Scout.colors
    Column(Modifier.fillMaxSize().clickable(onClick = onOpenAlerts)) {
        CardHead("Asks") { Text("${asks.size} open", style = ScoutType.meta, color = c.dim) }
        if (asks.isEmpty()) Text("Nothing is waiting on you.", style = ScoutType.bodySmall, color = c.dim, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
        asks.take(3).forEach { ask ->
            RowRule()
            Row(Modifier.height(28.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Lamp(LampState.Signal)
                Text(ask.sessionName.ifBlank { "agent" }, style = ScoutType.nameSmall, color = c.ink, modifier = Modifier.width(96.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ask.title.ifBlank { ask.description }, style = ScoutType.bodySmall, color = c.body, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(relativeTime(ask.createdMs), style = ScoutType.meta, color = c.dim)
            }
        }
    }
}

// -- Waiting on you ----------------------------------------------------------------

@Composable
private fun AskBox(item: InboxItem, busy: Boolean, error: String?, onOpen: () -> Unit, onDecide: (Boolean) -> Unit) {
    val c = Scout.colors
    LitBox(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Lamp(LampState.Signal)
                Tag(item.actionKind ?: item.kind, c.signal, Modifier.padding(start = 4.dp, end = 2.dp))
                HarnessMark(harnessOf(item.adapterType))
                Text(item.sessionName.ifBlank { item.adapterType }, style = ScoutType.mono11, color = c.second, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (item.risk != "low") Text("${item.risk} risk", style = ScoutType.micro, color = c.signal)
            }
            Text(item.title.ifBlank { "Needs your approval" }, style = ScoutType.name, color = c.ink, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
            val command = item.detail ?: item.description.takeIf { it.isNotBlank() && it != item.title }
            command?.let { CommandWell(it, Modifier.padding(start = 12.dp, top = 8.dp)) }
            if (error != null) Text(error, style = ScoutType.bodySmall, color = c.danger, modifier = Modifier.padding(start = 12.dp, top = 6.dp))
            Row(Modifier.padding(start = 12.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Open",
                    style = ScoutType.bodySmall,
                    color = c.second,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onOpen).defaultMinSize(minHeight = 40.dp).padding(vertical = 11.dp, horizontal = 2.dp),
                )
                Fill()
                if (busy) Text("Sending…", style = ScoutType.meta, color = c.dim)
                else {
                    ScoutButton("Deny", ButtonTone.Outline, { onDecide(false) })
                    ScoutButton("Allow once", ButtonTone.Plate, { onDecide(true) })
                }
            }
        }
    }
}

@Composable
fun CommandWell(command: String, modifier: Modifier = Modifier) {
    val c = Scout.colors
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = c.dim)) { append("$ ") }
            append(command)
        },
        style = ScoutType.mono12,
        color = c.body,
        maxLines = 6,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(c.well)
            .border(app.openscout.scout.ui.components.Hairline, c.line, RoundedCornerShape(6.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

// -- Rows ---------------------------------------------------------------------------

@Composable
private fun MovingRow(event: TailEvent, now: Long, onClick: () -> Unit) {
    val c = Scout.colors
    val fresh = now - event.tsMs < 5 * 60_000
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, end = 12.dp, top = 9.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Lamp(if (fresh) LampState.Live else LampState.Hollow)
            Text(relativeTime(event.tsMs, now).uppercase(), style = ScoutType.small, color = c.dim, modifier = Modifier.width(30.dp), maxLines = 1)
            HarnessMark(harnessOf(event.source))
            Text(event.project.ifBlank { event.cwd.substringAfterLast('/') }, style = ScoutType.mono11, color = c.second, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("…" + event.sessionId.takeLast(4), style = ScoutType.small, color = c.dim)
        }
        Text(
            replyText(event.summary),
            style = ScoutType.body,
            color = c.body,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp),
        )
    }
}

@Composable
private fun CoordinationRow(line: FleetActivity, now: Long, onClick: () -> Unit) {
    val c = Scout.colors
    Row(
        Modifier.fillMaxWidth().clickable(enabled = line.conversationId != null, onClick = onClick).defaultMinSize(minHeight = 36.dp).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(relativeTime(line.tsMs, now), style = ScoutType.small, color = c.dim, modifier = Modifier.width(30.dp).padding(top = 2.dp), maxLines = 1)
        Text(line.actorName ?: "—", style = ScoutType.nameSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(108.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (line.isAsk) Tag("ask", c.signal)
            Text(line.title, style = ScoutType.bodySmall, color = c.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Quiet(text: String) {
    Text(text, style = ScoutType.bodySmall, color = Scout.colors.dim, modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
}
