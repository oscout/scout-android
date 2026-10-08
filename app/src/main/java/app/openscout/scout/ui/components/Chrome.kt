package app.openscout.scout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.core.identity.TrustedBridge
import app.openscout.scout.ui.theme.Scout
import app.openscout.scout.ui.theme.ScoutType

fun machineLabel(machine: TrustedBridge?): String =
    machine?.name?.takeIf { it.isNotBlank() } ?: machine?.publicKeyHex?.let { "Computer ${it.take(4)}" } ?: "Not paired"

@Composable
fun linkColor(state: LinkState): Color = when (state) {
    is LinkState.Connected -> Scout.colors.life
    is LinkState.Connecting -> Scout.colors.signal
    is LinkState.Failed -> Scout.colors.danger
    LinkState.Idle -> Scout.colors.dim
}

fun linkLabel(state: LinkState): String = when (state) {
    is LinkState.Connected -> "Connected"
    is LinkState.Connecting -> state.detail
    is LinkState.Failed -> "Offline"
    LinkState.Idle -> "Idle"
}

/** Opens the navigation drawer; provided by the root so any top-level screen can show the burger. */
val LocalOpenDrawer = compositionLocalOf<(() -> Unit)?> { null }

/**
 * The masthead: burger (top level) or back (pushed), the title, trailing controls,
 * and a 0.5dp seam under it. 52dp, inside the status-bar inset.
 */
@Composable
fun Masthead(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Scout.colors
    val openDrawer = LocalOpenDrawer.current
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            when {
                onBack != null -> IconTarget("Back", onBack) { Glyph(Glyphs.BACK, size = 18.dp, color = c.second, stroke = 1.4f) }
                openDrawer != null -> IconTarget("Open navigation", openDrawer) { Glyph(Glyphs.BURGER, size = 18.dp, color = c.second, stroke = 1.4f) }
                else -> Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = if (subtitle == null) ScoutType.title else ScoutType.title.copy(fontSize = ScoutType.prose.fontSize * 1.07f), color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) Text(subtitle, style = ScoutType.meta, color = c.second, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            trailing()
        }
        MastRule()
    }
}

/**
 * Which computer the fleet reads from: lamps for its link, then its name. One pill,
 * never a card (the iPhone's host pill). Taps open the drawer's host list.
 */
@Composable
fun HostPill(machine: TrustedBridge?, link: LinkState, onClick: () -> Unit, extraHosts: Int = 0) {
    val c = Scout.colors
    val shape = RoundedCornerShape(999.dp)
    Box(
        Modifier.height(48.dp).clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "Host: ${machineLabel(machine)}, ${linkLabel(link)}" },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 4.dp).height(28.dp).clip(shape)
                .background(Brush.verticalGradient(listOf(c.boxTop, Color.Transparent)))
                .border(Hairline, c.line, shape)
                .padding(start = 10.dp, end = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Lamp(
                when (link) {
                    is LinkState.Connected -> LampState.Live
                    is LinkState.Connecting -> LampState.Signal
                    else -> LampState.Hollow
                },
            )
            Text(machineLabel(machine), style = ScoutType.mono11, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 120.dp).padding(start = 2.dp))
            if (extraHosts > 0) Text("+$extraHosts", style = ScoutType.meta, color = c.dim)
            Glyph(Glyphs.DOWN, size = 10.dp, color = c.second, stroke = 1.4f)
        }
    }
}

/** Status lamp + machine name + link word, for screens that only need a readout. */
@Composable
fun HostReadout(machine: TrustedBridge?, state: LinkState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StatusDot(linkColor(state), pulsing = state is LinkState.Connecting)
        Spacer(Modifier.width(6.dp))
        Text("${machineLabel(machine)} · ${linkLabel(state)}", style = ScoutType.meta, color = Scout.colors.second, maxLines = 1)
    }
}

/** The pushed-screen header, now the D masthead. `scrollBehavior` is accepted and ignored: the seam stays put. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Masthead(title, subtitle, onBack, actions)
}
