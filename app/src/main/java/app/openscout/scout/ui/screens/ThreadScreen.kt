package app.openscout.scout.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.core.model.CommsMessage
import app.openscout.scout.ui.AppViewModel
import app.openscout.scout.ui.components.EmptyState
import app.openscout.scout.ui.components.Eyebrow
import app.openscout.scout.ui.components.InlineError
import app.openscout.scout.ui.components.MonoText
import app.openscout.scout.ui.components.OfflineBanner
import app.openscout.scout.ui.components.ScoutTopBar
import app.openscout.scout.ui.components.StatusDot
import app.openscout.scout.ui.components.relativeTime
import app.openscout.scout.ui.theme.Scout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(vm: AppViewModel, conversationId: String?, title: String, agentId: String?, onBack: () -> Unit) {
    LaunchedEffect(conversationId, agentId) { vm.openThread(conversationId, title, agentId) }
    DisposableEffect(Unit) { onDispose { vm.closeThread() } }

    val thread by vm.thread.collectAsStateWithLifecycle()
    val link by vm.linkState.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    val t = thread
    val messages = (t?.messages?.data.orEmpty() + t?.pending.orEmpty())
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(0) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            ScoutTopBar(
                title = title,
                subtitle = when {
                    t?.lifecycle != null -> t.lifecycle.replace('_', ' ')
                    t?.conversationId != null -> t.conversationId
                    else -> "new direct message"
                },
                onBack = onBack,
                actions = { IconButton(onClick = vm::reloadThread) { Icon(Icons.Outlined.Refresh, "Refresh") } },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.navigationBarsPadding().imePadding()) {
                    t?.sendError?.let { InlineError(it) }
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.Bottom) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (agentId != null) "Message $title" else "Message") },
                            maxLines = 6,
                            shape = RoundedCornerShape(8.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = Scout.tokens.inset,
                                focusedContainerColor = Scout.tokens.inset,
                            ),
                        )
                        Spacer(Modifier.size(8.dp))
                        FilledIconButton(
                            onClick = { vm.send(draft); draft = "" },
                            enabled = draft.isNotBlank() && link is LinkState.Connected,
                            modifier = Modifier.size(52.dp),
                            shape = RoundedCornerShape(8.dp),
                        ) { Icon(Icons.AutoMirrored.Filled.Send, "Send") }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                t == null || (t.messages.data == null && t.messages.loading) -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    LoadingLine("Loading conversation…")
                }
                t.messages.data == null && t.messages.error != null -> InlineError(t.messages.error)
                messages.isEmpty() -> EmptyState(
                    Icons.Outlined.Forum,
                    "Say hello",
                    if (agentId != null) "Your message goes to $title through the broker on your computer." else "No messages in this conversation yet.",
                )
                else -> LazyColumn(
                    state = listState,
                    reverseLayout = true,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.Bottom),
                ) {
                    val reversed = messages.asReversed()
                    items(reversed.size, key = { reversed[it].id }) { index ->
                        val m = reversed[index]
                        val older = reversed.getOrNull(index + 1)
                        val grouped = older != null && older.actorId == m.actorId && m.createdMs - older.createdMs < 5 * 60_000
                        MessageBubble(m, showAuthor = !grouped, pending = m.id.startsWith("local-"))
                    }
                }
            }
            if (link is LinkState.Failed) {
                OfflineBanner((link as LinkState.Failed).message, vm::retryNow, Modifier.align(Alignment.TopCenter))
            }
        }
    }
}

@Composable
private fun MessageBubble(m: CommsMessage, showAuthor: Boolean, pending: Boolean) {
    val mine = m.fromOperator
    val shape = RoundedCornerShape(
        topStart = 10.dp, topEnd = 10.dp,
        bottomStart = if (mine) 10.dp else 3.dp,
        bottomEnd = if (mine) 3.dp else 10.dp,
    )
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = if (showAuthor) 4.dp else 0.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (showAuthor) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) {
                if (!mine) {
                    StatusDot(
                        if (m.authorKind == "system") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        size = 6.dp,
                    )
                    Spacer(Modifier.size(6.dp))
                }
                Eyebrow(if (mine) "You" else m.authorLabel.ifBlank { m.actorId })
                Spacer(Modifier.size(8.dp))
                MonoText(if (pending) "sending…" else relativeTime(m.createdMs))
            }
        }
        val bg = if (mine) MaterialTheme.colorScheme.primary.copy(alpha = if (Scout.tokens.isDark) 0.22f else 0.12f)
        else if (Scout.tokens.isDark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerLowest
        Box(
            Modifier.widthIn(max = 320.dp)
                .background(bg, shape)
                .border(1.dp, if (mine) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f) else MaterialTheme.colorScheme.outlineVariant, shape)
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            SelectionContainer {
                Text(
                    m.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (pending) 0.6f else 1f),
                )
            }
        }
        if (!m.attachments.isNullOrEmpty()) {
            Spacer(Modifier.height(4.dp))
            MonoText("${m.attachments.size} attachment(s) · open on your computer")
        }
    }
}
