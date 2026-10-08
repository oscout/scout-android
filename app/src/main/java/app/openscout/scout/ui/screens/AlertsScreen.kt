package app.openscout.scout.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.core.model.InboxItem
import app.openscout.scout.ui.AppViewModel
import app.openscout.scout.ui.components.Chip
import app.openscout.scout.ui.components.EmptyState
import app.openscout.scout.ui.components.Eyebrow
import app.openscout.scout.ui.components.InlineError
import app.openscout.scout.ui.components.ListContentPadding
import app.openscout.scout.ui.components.MonoText
import app.openscout.scout.ui.components.OfflineBanner
import app.openscout.scout.ui.components.ScoutCard
import app.openscout.scout.ui.components.ScoutTopBar
import app.openscout.scout.ui.components.StatusDot
import app.openscout.scout.ui.components.harnessLabel
import app.openscout.scout.ui.components.relativeTime
import app.openscout.scout.ui.theme.Scout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(vm: AppViewModel, onOpenThread: (String, String) -> Unit) {
    val inbox by vm.inbox.collectAsStateWithLifecycle()
    val link by vm.linkState.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { ScoutTopBar("Alerts", subtitle = "requests waiting on you") },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = inbox.loading && inbox.data != null,
            onRefresh = vm::refreshInbox,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = ListContentPadding) {
                if (link is LinkState.Failed) item { OfflineBanner((link as LinkState.Failed).message, vm::retryNow) }
                val items = inbox.data
                when {
                    items == null && inbox.error != null -> item { InlineError(inbox.error!!) }
                    items == null -> item { LoadingLine("Checking for requests…") }
                    items.isEmpty() -> item {
                        EmptyState(
                            Icons.Outlined.TaskAlt,
                            "Nothing needs you",
                            "Approvals, questions, and asks from your agents land here the moment they're blocked on you.",
                        )
                    }
                    else -> items(items, key = { it.id }) { item ->
                        InboxCard(vm, item, onOpenThread, Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InboxCard(vm: AppViewModel, item: InboxItem, onOpenThread: (String, String) -> Unit, modifier: Modifier = Modifier) {
    val busyMap by vm.inboxBusy.collectAsStateWithLifecycle()
    val busy = busyMap.containsKey(item.id) && busyMap[item.id] == null
    val error = busyMap[item.id]
    var answer by remember(item.id) { mutableStateOf("") }
    val riskColor = when (item.risk) {
        "high" -> Scout.tokens.danger
        "medium" -> Scout.tokens.warn
        else -> MaterialTheme.colorScheme.tertiary
    }
    ScoutCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(riskColor, size = 8.dp, pulsing = true)
            Spacer(Modifier.width(8.dp))
            Eyebrow(
                when {
                    item.isDecidableApproval -> "Approval"
                    item.isAnswerableQuestion -> "Question"
                    item.isConversationAsk -> "Ask"
                    item.isTerminalPrompt -> "Terminal prompt"
                    else -> item.kind.ifBlank { "Request" }
                },
                color = riskColor,
            )
            Spacer(Modifier.weight(1f))
            MonoText(relativeTime(item.createdMs))
        }
        Spacer(Modifier.height(8.dp))
        Text(item.title.ifBlank { item.sessionName }, style = MaterialTheme.typography.titleMedium)
        if (item.description.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(item.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 6)
        }
        item.detail?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(6.dp))
            MonoText(it, maxLines = 4)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MonoText(item.sessionName, modifier = Modifier.weight(1f, fill = false))
            harnessLabel(item.adapterType)?.let { Spacer(Modifier.width(8.dp)); Chip(it) }
        }
        Spacer(Modifier.height(10.dp))
        when {
            item.isDecidableApproval -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { vm.decide(item, approve = false) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Deny") }
                Button(onClick = { vm.decide(item, approve = true) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Approve") }
            }
            item.isAnswerableQuestion -> Column {
                if (!item.options.isNullOrEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.options.forEach { opt -> OutlinedButton(onClick = { vm.answer(item, opt) }, enabled = !busy) { Text(opt) } }
                    }
                } else {
                    OutlinedTextField(answer, { answer = it }, Modifier.fillMaxWidth(), placeholder = { Text("Your answer") }, singleLine = true)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.answer(item, answer.trim()) }, enabled = !busy && answer.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Send answer") }
                }
            }
            item.isConversationAsk -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.options.orEmpty().forEach { opt -> OutlinedButton(onClick = { vm.replyToAsk(item, opt) }, enabled = !busy) { Text(opt) } }
                Button(onClick = { onOpenThread(item.sessionId, item.sessionName.ifBlank { item.title }) }) { Text("Open chat") }
            }
            item.isTerminalPrompt -> Text(
                "Answer this at the terminal on your computer — Scout reads the prompt but never types into the pane.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> TextButton(onClick = { onOpenThread(item.sessionId, item.sessionName) }) { Text("Open") }
        }
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
}
