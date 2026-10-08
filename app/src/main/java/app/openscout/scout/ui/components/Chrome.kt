package app.openscout.scout.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.core.identity.TrustedBridge
import app.openscout.scout.ui.theme.Scout

fun machineLabel(machine: TrustedBridge?): String =
    machine?.name?.takeIf { it.isNotBlank() } ?: machine?.publicKeyHex?.let { "Computer ${it.take(4)}" } ?: "Not paired"

@Composable
fun linkColor(state: LinkState): Color = when (state) {
    is LinkState.Connected -> Scout.tokens.ok
    is LinkState.Connecting -> Scout.tokens.warn
    is LinkState.Failed -> Scout.tokens.danger
    LinkState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
}

fun linkLabel(state: LinkState): String = when (state) {
    is LinkState.Connected -> "Connected"
    is LinkState.Connecting -> state.detail
    is LinkState.Failed -> "Offline"
    LinkState.Idle -> "Idle"
}

/** Host scope readout: status lamp + machine name + link word, in the masthead. */
@Composable
fun HostReadout(machine: TrustedBridge?, state: LinkState, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StatusDot(linkColor(state), pulsing = state is LinkState.Connecting, size = 7.dp)
        Spacer(Modifier.width(6.dp))
        Eyebrow("${machineLabel(machine)} · ${linkLabel(state)}")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoutTopBar(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    actions: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle != null) MonoText(subtitle)
            }
        },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        },
        actions = actions,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
    )
}
