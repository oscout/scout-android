package app.openscout.scout.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.core.identity.TrustedBridge
import app.openscout.scout.ui.components.Glyph
import app.openscout.scout.ui.components.Glyphs
import app.openscout.scout.ui.components.Hairline
import app.openscout.scout.ui.components.HexMark
import app.openscout.scout.ui.components.IconTarget
import app.openscout.scout.ui.components.Lamp
import app.openscout.scout.ui.components.LampState
import app.openscout.scout.ui.components.SectionHead
import app.openscout.scout.ui.components.linkLabel
import app.openscout.scout.ui.components.machineLabel
import app.openscout.scout.ui.theme.Scout
import app.openscout.scout.ui.theme.ScoutType

private val GEAR = "M9.63 4.95 Q10.69 4.51 10.84 3.37 L10.85 3.29 Q10.99 2.15 12.14 2.15 L11.86 2.15 Q13.01 2.15 13.15 3.29 L13.16 3.37 Q13.31 4.51 14.37 4.95 L15.31 5.34 Q16.37 5.78 17.28 5.08 L17.34 5.03 Q18.25 4.32 19.06 5.14 L18.86 4.94 Q19.68 5.75 18.97 6.66 L18.92 6.72 Q18.22 7.63 18.66 8.69 L19.05 9.63 Q19.49 10.69 20.63 10.84 L20.71 10.85 Q21.85 10.99 21.85 12.14 L21.85 11.86 Q21.85 13.01 20.71 13.15 L20.63 13.16 Q19.49 13.31 19.05 14.37 L18.66 15.31 Q18.22 16.37 18.92 17.28 L18.97 17.34 Q19.68 18.25 18.86 19.06 L19.06 18.86 Q18.25 19.68 17.34 18.97 L17.28 18.92 Q16.37 18.22 15.31 18.66 L14.37 19.05 Q13.31 19.49 13.16 20.63 L13.15 20.71 Q13.01 21.85 11.86 21.85 L12.14 21.85 Q10.99 21.85 10.85 20.71 L10.84 20.63 Q10.69 19.49 9.63 19.05 L8.69 18.66 Q7.63 18.22 6.72 18.92 L6.66 18.97 Q5.75 19.68 4.94 18.86 L5.14 19.06 Q4.32 18.25 5.03 17.34 L5.08 17.28 Q5.78 16.37 5.34 15.31 L4.95 14.37 Q4.51 13.31 3.37 13.16 L3.29 13.15 Q2.15 13.01 2.15 11.86 L2.15 12.14 Q2.15 10.99 3.29 10.85 L3.37 10.84 Q4.51 10.69 4.95 9.63 L5.34 8.69 Q5.78 7.63 5.08 6.72 L5.03 6.66 Q4.32 5.75 5.14 4.94 L4.94 5.14 Q5.75 4.32 6.66 5.03 L6.72 5.08 Q7.63 5.78 8.69 5.34 Z M8.8 12 a3.2 3.2 0 1 0 6.4 0 a3.2 3.2 0 1 0 -6.4 0"

/** The phone's way to everywhere: the iPhone drawer, as a modal navigation drawer. */
@Composable
fun ScoutDrawer(
    isAt: (Any) -> Boolean,
    alerts: Int,
    unreadChats: Int,
    agentCount: Int?,
    machines: List<TrustedBridge>,
    activeKey: String?,
    link: LinkState,
    onNewSession: () -> Unit,
    onNavigate: (Any) -> Unit,
    onSwitchHost: (String) -> Unit,
    onPairHost: () -> Unit,
    onSettings: () -> Unit,
) {
    val c = Scout.colors
    val shape = RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
    Column(
        Modifier
            .fillMaxHeight()
            .width(316.dp)
            .clip(shape)
            .drawBehind {
                drawRect(Brush.verticalGradient(listOf(c.sheetTop, c.sheetBottom)))
                drawRect(Brush.radialGradient(listOf(c.keyLight, Color.Transparent), Offset(size.width * 0.3f, -size.height * 0.04f), size.width * 1.4f))
                drawLine(c.line, Offset(size.width, 0f), Offset(size.width, size.height), Hairline.toPx())
            }
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().height(52.dp).padding(start = 18.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HexMark(20.dp, c.ink)
            Text("Scout", style = ScoutType.title, color = c.ink, modifier = Modifier.weight(1f))
        }

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            NavRow("New session", glyph = null, selected = false, outlined = true, onClick = onNewSession)

            SectionHead("Go to", modifier = Modifier.padding(horizontal = 16.dp))
            NavRow("Home", Glyphs.HOME, isAt(HomeRoute), onClick = { onNavigate(HomeRoute) })
            NavRow("Chats", Glyphs.CHATS, isAt(ChatsRoute), count = unreadChats.takeIf { it > 0 }?.toString(), onClick = { onNavigate(ChatsRoute) })
            NavRow("Agents", Glyphs.AGENTS, isAt(AgentsRoute), count = agentCount?.toString(), countTone = false, onClick = { onNavigate(AgentsRoute) })
            NavRow("Ops", Glyphs.OPS, isAt(TailRoute), onClick = { onNavigate(TailRoute) })
            NavRow("Alerts", Glyphs.ALERTS, isAt(AlertsRoute), count = alerts.takeIf { it > 0 }?.toString(), onClick = { onNavigate(AlertsRoute) })

            SectionHead("Hosts", count = machines.size.toString(), modifier = Modifier.padding(horizontal = 16.dp))
            machines.forEach { m ->
                val active = m.publicKeyHex.equals(activeKey, ignoreCase = true)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = !active, role = Role.Button) { onSwitchHost(m.publicKeyHex) }
                        .defaultMinSize(minHeight = 44.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Lamp(if (active && link is LinkState.Connected) LampState.Live else if (active) LampState.Signal else LampState.Hollow)
                    Text(machineLabel(m), style = ScoutType.mono12, color = if (active) c.ink else c.second, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (active) linkLabel(link).lowercase() else "switch", style = ScoutType.meta, color = c.dim, maxLines = 1)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp)).clickable(role = Role.Button, onClick = onPairHost)
                    .defaultMinSize(minHeight = 44.dp).padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Glyph(Glyphs.PLUS, size = 14.dp, color = c.second)
                Text("Pair a host", style = ScoutType.bodySmall, color = c.second)
            }
            Spacer(Modifier.height(12.dp))
        }

        Box(Modifier.fillMaxWidth().height(Hairline).background(c.rule))
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Lamp(if (link is LinkState.Connected) LampState.Live else LampState.Hollow)
            Text(
                when (link) {
                    is LinkState.Connected -> "encrypted · ${link.route.label.lowercase()}"
                    else -> linkLabel(link).lowercase()
                },
                style = ScoutType.meta, color = c.second, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            IconTarget("Settings", onSettings) { Glyph(GEAR, size = 18.dp, color = c.second, grid = 24f, stroke = 1.4f) }
        }
    }
}

@Composable
private fun NavRow(
    label: String,
    glyph: String?,
    selected: Boolean,
    count: String? = null,
    countTone: Boolean = true,
    outlined: Boolean = false,
    onClick: () -> Unit,
) {
    val c = Scout.colors
    val shape = RoundedCornerShape(8.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(shape)
            .then(if (selected) Modifier.background(Brush.verticalGradient(listOf(c.plateTop, c.plateBottom))) else Modifier)
            .then(if (outlined) Modifier.border(Hairline, c.line, shape) else Modifier)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics { this.selected = selected }
            .height(48.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (glyph != null) Glyph(glyph, size = 20.dp, color = if (selected) c.plateText else c.second, grid = 24f, stroke = 1.3f)
        else HexMark(18.dp, c.second, ringOnly = true)
        Text(label, style = ScoutType.name.copy(fontSize = ScoutType.prose.fontSize), color = if (selected) c.plateText else c.body, modifier = Modifier.weight(1f))
        if (count != null) {
            if (countTone) {
                Text(
                    count,
                    style = ScoutType.small,
                    color = c.signal,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).border(Hairline, c.signal.copy(alpha = 0.5f), RoundedCornerShape(999.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                )
            } else {
                Text(count, style = ScoutType.meta, color = c.dim)
            }
        }
    }
}
