package app.openscout.scout.ui.screens

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openscout.scout.BuildConfig
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.core.bridge.LogLine
import app.openscout.scout.core.identity.TrustedBridge
import app.openscout.scout.ui.AppViewModel
import app.openscout.scout.ui.components.MonoText
import app.openscout.scout.ui.components.ScoutTopBar
import app.openscout.scout.ui.components.SectionHeader
import app.openscout.scout.ui.components.StatusDot
import app.openscout.scout.ui.components.linkColor
import app.openscout.scout.ui.components.linkLabel
import app.openscout.scout.ui.components.machineLabel
import app.openscout.scout.ui.components.relativeTime
import app.openscout.scout.ui.theme.Scout
import app.openscout.scout.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, onPairAnother: () -> Unit, onAllForgotten: () -> Unit) {
    val machines by vm.machines.collectAsStateWithLifecycle()
    val activeKey by vm.activeKey.collectAsStateWithLifecycle()
    val link by vm.linkState.collectAsStateWithLifecycle()
    val log by vm.connectionLog.collectAsStateWithLifecycle()
    val themeMode by vm.settings.themeMode.collectAsStateWithLifecycle()
    val wallpaper by vm.settings.wallpaperColor.collectAsStateWithLifecycle()
    var forgetting by remember { mutableStateOf<TrustedBridge?>(null) }
    var renaming by remember { mutableStateOf<TrustedBridge?>(null) }
    var showLog by remember { mutableStateOf(false) }
    val itemColors = ListItemDefaults.colors(containerColor = Color.Transparent)

    Scaffold(containerColor = Color.Transparent, topBar = { ScoutTopBar("Settings", onBack = onBack) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).navigationBarsPadding()) {
            item { SectionHeader("Paired computers") }
            items(machines, key = { it.publicKeyHex }) { m ->
                val active = m.publicKeyHex.equals(activeKey, true)
                ListItem(
                    modifier = Modifier.clickable(enabled = !active) { vm.switchMachine(m.publicKeyHex) },
                    colors = itemColors,
                    leadingContent = { Icon(if (active) Icons.Filled.CheckCircle else Icons.Outlined.Computer, null, tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant) },
                    headlineContent = { Text(machineLabel(m)) },
                    supportingContent = {
                        Column {
                            if (active) Row(verticalAlignment = Alignment.CenterVertically) {
                                StatusDot(linkColor(link), size = 6.dp); Spacer(Modifier.width(6.dp)); MonoText(linkLabel(link).lowercase())
                            }
                            MonoText("key ${m.publicKeyHex.take(16)} · paired ${relativeTime(m.pairedAt)}")
                        }
                    },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { renaming = m }) { Icon(Icons.Outlined.Edit, "Rename") }
                            IconButton(onClick = { forgetting = m }) { Icon(Icons.Outlined.DeleteOutline, "Forget") }
                        }
                    },
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable(onClick = onPairAnother),
                    colors = itemColors,
                    leadingContent = { Icon(Icons.Outlined.Add, null) },
                    headlineContent = { Text("Pair another computer") },
                )
            }

            item { SectionHeader("Connection") }
            item {
                val connected = link as? LinkState.Connected
                Column(Modifier.padding(horizontal = 20.dp)) {
                    MonoText("route   ${connected?.route?.label ?: "—"}", color = MaterialTheme.colorScheme.onSurface)
                    MonoText("relay   ${connected?.relayUrl ?: "—"}", color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                    MonoText("cipher  Noise_IK_25519_AESGCM_SHA256", color = MaterialTheme.colorScheme.onSurface)
                    MonoText("phone   ${vm.phoneFingerprint}", color = MaterialTheme.colorScheme.onSurface)
                    TextButton(onClick = { showLog = !showLog }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                        Text(if (showLog) "Hide connection log" else "Show connection log (${log.size})")
                    }
                    if (showLog) {
                        log.asReversed().take(60).forEach { line ->
                            MonoText(
                                "${line.clock}  ${line.text}",
                                maxLines = 3,
                                color = when (line.level) {
                                    LogLine.Level.Error -> Scout.tokens.danger
                                    LogLine.Level.Warning -> Scout.tokens.warn
                                    LogLine.Level.Success -> Scout.tokens.ok
                                    LogLine.Level.Info -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }

            item { SectionHeader("Appearance") }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    ThemeMode.entries.forEachIndexed { i, mode ->
                        SegmentedButton(
                            selected = themeMode == mode,
                            onClick = { vm.settings.setThemeMode(mode) },
                            shape = SegmentedButtonDefaults.itemShape(i, ThemeMode.entries.size),
                        ) { Text(mode.name) }
                    }
                }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item {
                    ListItem(
                        colors = itemColors,
                        headlineContent = { Text("Wallpaper colors") },
                        supportingContent = { Text("Use Material You colors instead of Scout's warm palette") },
                        trailingContent = { Switch(checked = wallpaper, onCheckedChange = vm.settings::setWallpaperColor) },
                    )
                }
            }

            item { SectionHeader("Not on Android yet") }
            item {
                Text(
                    "Terminal (SSH shell), voice, push notifications, widgets and the Spaces web surface are iOS-only for now.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
            item {
                HorizontalDivider(Modifier.padding(top = 24.dp), color = MaterialTheme.colorScheme.outlineVariant)
                MonoText("Scout for Android ${BuildConfig.VERSION_NAME}", modifier = Modifier.padding(20.dp))
            }
        }
    }

    forgetting?.let { m ->
        AlertDialog(
            onDismissRequest = { forgetting = null },
            title = { Text("Forget ${machineLabel(m)}?") },
            text = { Text("This phone stops trusting that computer. You'll need its pairing QR to reconnect.") },
            confirmButton = {
                TextButton(onClick = {
                    forgetting = null
                    vm.forgetMachine(m.publicKeyHex)
                    if (!vm.isPaired) onAllForgotten()
                }) { Text("Forget", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { forgetting = null }) { Text("Cancel") } },
        )
    }
    renaming?.let { m ->
        var name by remember(m.publicKeyHex) { mutableStateOf(m.name ?: "") }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename computer") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
            confirmButton = { TextButton(onClick = { vm.renameMachine(m.publicKeyHex, name); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}
