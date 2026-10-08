package app.openscout.scout.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import app.openscout.scout.ui.components.Glyph
import app.openscout.scout.ui.components.Glyphs
import app.openscout.scout.ui.components.Hairline
import app.openscout.scout.ui.components.Lamp
import app.openscout.scout.ui.components.LampState
import app.openscout.scout.ui.components.LitBox
import app.openscout.scout.ui.theme.ScoutType

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
                    agentId != null || t?.conversationId?.startsWith("dm") == true -> "direct"
                    t?.conversationId != null -> "channel"
                    else -> "new direct message"
                },
                onBack = onBack,
                actions = { IconButton(onClick = vm::reloadThread) { Icon(Icons.Outlined.Refresh, "Refresh") } },
            )
        },
        bottomBar = {
            val c = Scout.colors
            Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(start = 8.dp, end = 8.dp, top = 6.dp, bottom = 8.dp)) {
                t?.sendError?.let { InlineError(it) }
                LitBox(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.Bottom) {
                        TextField(
                            value = draft,
                            onValueChange = { draft = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(if (agentId != null) "Message $title…" else "Message…", style = ScoutType.prose, color = c.dim) },
                            textStyle = ScoutType.prose.copy(color = c.ink),
                            maxLines = 6,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                cursorColor = c.ink,
                            ),
                        )
                        val canSend = draft.isNotBlank() && link is LinkState.Connected
                        Box(
                            Modifier.padding(bottom = 4.dp).size(44.dp).clip(RoundedCornerShape(8.dp))
                                .background(Brush.verticalGradient(listOf(c.plateTop, c.plateBottom)))
                                .clickable(enabled = canSend, role = Role.Button) { vm.send(draft); draft = "" }
                                .semantics { contentDescription = "Send" },
                            contentAlignment = Alignment.Center,
                        ) {
                            Glyph(Glyphs.SEND, size = 16.dp, color = c.plateText.copy(alpha = if (canSend) 1f else 0.35f), stroke = 1.4f)
                        }
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
    val c = Scout.colors
    val mine = m.fromOperator
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = if (showAuthor) 6.dp else 0.dp),
        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
    ) {
        if (showAuthor && !mine) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.padding(bottom = 4.dp)) {
                Lamp(if (m.authorKind == "system") LampState.Hollow else LampState.Live)
                Text(m.authorLabel.ifBlank { m.actorId }, style = ScoutType.nameSmall, color = c.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(relativeTime(m.createdMs), style = ScoutType.meta, color = c.dim)
            }
        }
        if (mine) {
            val shape = RoundedCornerShape(10.dp)
            Box(
                Modifier.widthIn(max = 300.dp)
                    .clip(shape)
                    .background(Brush.verticalGradient(listOf(c.popTop, c.popBottom)))
                    .border(Hairline, c.line, shape)
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                SelectionContainer { Text(m.body, style = ScoutType.prose, color = c.ink.copy(alpha = if (pending) 0.55f else 1f)) }
            }
            if (showAuthor || pending) {
                Text(if (pending) "sending…" else relativeTime(m.createdMs), style = ScoutType.meta, color = c.dim, modifier = Modifier.padding(top = 3.dp, end = 2.dp))
            }
        } else {
            SelectionContainer { Text(m.body, style = ScoutType.prose, color = c.body, modifier = Modifier.padding(start = 13.dp)) }
        }
        if (!m.attachments.isNullOrEmpty()) {
            Text("${m.attachments.size} attachment(s) · open on your computer", style = ScoutType.meta, color = c.dim, modifier = Modifier.padding(start = 13.dp, top = 4.dp))
        }
    }
}
