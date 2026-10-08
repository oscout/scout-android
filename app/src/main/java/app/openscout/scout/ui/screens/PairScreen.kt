package app.openscout.scout.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.ui.AppViewModel
import app.openscout.scout.ui.PairingUi
import app.openscout.scout.ui.components.Eyebrow
import app.openscout.scout.ui.components.MonoText
import app.openscout.scout.ui.components.PrimaryAction
import app.openscout.scout.ui.components.ScoutMark
import app.openscout.scout.ui.components.SecondaryAction
import app.openscout.scout.ui.components.SignalPanel
import app.openscout.scout.ui.components.StatusDot
import app.openscout.scout.ui.theme.Mono
import app.openscout.scout.ui.theme.Scout

@Suppress("DEPRECATION")
@Composable
fun PairScreen(
    vm: AppViewModel,
    adding: Boolean,
    onScan: () -> Unit,
    onPaired: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val pairing by vm.pairing.collectAsStateWithLifecycle()
    val link by vm.linkState.collectAsStateWithLifecycle()
    var pasteOpen by remember { mutableStateOf(false) }
    var pasteText by remember { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current

    // Pairing finished: the connection is live with a trusted bridge.
    var sawWorking by remember { mutableStateOf(false) }
    LaunchedEffect(pairing, link) {
        if (pairing is PairingUi.Working) sawWorking = true
        if (sawWorking && pairing is PairingUi.Idle && link is LinkState.Connected) onPaired()
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            }
        }
        Spacer(Modifier.height(if (onBack != null) 8.dp else 32.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoutMark(size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Text("Scout", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
        }
        Spacer(Modifier.height(36.dp))
        Text(
            if (adding) "Pair another computer." else "Pair with your computer.",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Reach the agents running on your machine from this phone. Show the pairing QR in Scout on your computer — or run `scout pair` in a terminal — then scan it here.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))

        val working = pairing as? PairingUi.Working
        val failed = pairing as? PairingUi.Failed
        SignalPanel(active = working != null, accent = if (failed != null) Scout.tokens.danger else MaterialTheme.colorScheme.primary, modifier = Modifier.fillMaxWidth()) {
            Eyebrow("Trust", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            when {
                working != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(working.step + "…", style = MaterialTheme.typography.titleSmall)
                        val detail = (link as? LinkState.Connecting)?.detail
                        if (detail != null) MonoText(detail.lowercase())
                    }
                }
                failed != null -> Row(verticalAlignment = Alignment.Top) {
                    StatusDot(Scout.tokens.danger, Modifier.padding(top = 6.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(failed.message, style = MaterialTheme.typography.bodyMedium)
                }
                else -> Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Direct and end-to-end encrypted (Noise). Your computer confirms this phone's key; no cloud account is involved.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            MonoText("this phone · ${vm.phoneFingerprint}")
        }

        Spacer(Modifier.height(24.dp))
        PrimaryAction("Scan pairing QR", onScan, Modifier.fillMaxWidth(), enabled = working == null, icon = Icons.Outlined.QrCodeScanner)
        Spacer(Modifier.height(12.dp))
        SecondaryAction(
            "Paste pairing link",
            {
                pasteText = clipboard.getText()?.text?.takeIf { it.contains("pair") || it.trim().startsWith("{") } ?: ""
                pasteOpen = true
            },
            Modifier.fillMaxWidth(),
            enabled = working == null,
            icon = Icons.Outlined.ContentPaste,
        )

        AnimatedVisibility(vm.isEmulator) {
            Column(Modifier.padding(top = 28.dp)) {
                Eyebrow("Running in the emulator")
                Spacer(Modifier.height(8.dp))
                Text(
                    "The emulator can't scan a QR off your screen. From the repo on the host, run:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Scout.tokens.inset,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        "apps/android/scripts/pair-emulator.sh",
                        fontFamily = Mono,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "It hands this app the host's live pairing link over adb. The advertised relay is tried first, then the 10.0.2.2 host alias.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (pasteOpen) {
        AlertDialog(
            onDismissRequest = { pasteOpen = false },
            title = { Text("Pairing link") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste the link from “Copy pairing link” in Scout on your computer (scout://pair…, an openscout.app/pair link, or the raw JSON).",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = pasteText,
                        onValueChange = { pasteText = it },
                        modifier = Modifier.fillMaxWidth().height(140.dp),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = Mono),
                        placeholder = { Text("scout://pair?payload=…") },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { pasteOpen = false; vm.pair(pasteText) }, enabled = pasteText.isNotBlank()) { Text("Pair") }
            },
            dismissButton = { TextButton(onClick = { pasteOpen = false }) { Text("Cancel") } },
        )
    }
}
