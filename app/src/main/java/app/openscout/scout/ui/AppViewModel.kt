package app.openscout.scout.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.openscout.scout.ScoutApplication
import app.openscout.scout.core.bridge.BridgeEvent
import app.openscout.scout.core.bridge.BridgeException
import app.openscout.scout.core.bridge.FailureKind
import app.openscout.scout.core.bridge.LinkState
import app.openscout.scout.core.bridge.RpcException
import app.openscout.scout.core.crypto.toHex
import app.openscout.scout.core.identity.TrustedBridge
import app.openscout.scout.core.model.ActivityItem
import app.openscout.scout.core.model.Agent
import app.openscout.scout.core.model.CommsConversation
import app.openscout.scout.core.model.CommsMessage
import app.openscout.scout.core.model.FleetSnapshot
import app.openscout.scout.core.model.Heartrate
import app.openscout.scout.core.model.ServiceBudgets
import app.openscout.scout.core.model.InboxItem
import app.openscout.scout.core.model.MobileHome
import app.openscout.scout.core.model.TailEvent
import app.openscout.scout.core.pairing.QRPayload
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class Loadable<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val updatedAt: Long = 0,
)

sealed interface PairingUi {
    data object Idle : PairingUi
    data class Working(val step: String) : PairingUi
    data class Failed(val message: String) : PairingUi
}

data class ThreadUi(
    val conversationId: String?,
    val title: String,
    val agentId: String? = null,
    val messages: Loadable<List<CommsMessage>> = Loadable(loading = true),
    val pending: List<CommsMessage> = emptyList(),
    val sendError: String? = null,
    val lifecycle: String? = null,
)

sealed interface NewSessionUi {
    data object Idle : NewSessionUi
    data object Starting : NewSessionUi
    data class Started(val conversationId: String, val title: String, val agentId: String) : NewSessionUi
    data class Failed(val message: String) : NewSessionUi
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ScoutApplication
    private val connection = app.connection
    private val client = app.client
    private val identity = app.identity
    val settings = app.settings
    val isEmulator = ScoutApplication.isProbablyEmulator()

    val linkState: StateFlow<LinkState> = connection.state
    val connectionLog = connection.log

    private val _machines = MutableStateFlow(identity.trustedBridges())
    val machines: StateFlow<List<TrustedBridge>> = _machines.asStateFlow()
    private val _activeKey = MutableStateFlow(identity.activeConnectionInfo()?.publicKeyHex)
    val activeKey: StateFlow<String?> = _activeKey.asStateFlow()

    val phoneFingerprint: String by lazy { identity.loadOrCreateIdentity().publicKey.toHex().take(16) }

    private val _pairing = MutableStateFlow<PairingUi>(PairingUi.Idle)
    val pairing: StateFlow<PairingUi> = _pairing.asStateFlow()

    private val _home = MutableStateFlow(Loadable<MobileHome>())
    val home: StateFlow<Loadable<MobileHome>> = _home.asStateFlow()
    private val _activity = MutableStateFlow(Loadable<List<ActivityItem>>())
    val activity: StateFlow<Loadable<List<ActivityItem>>> = _activity.asStateFlow()
    private val _inbox = MutableStateFlow(Loadable<List<InboxItem>>())
    val inbox: StateFlow<Loadable<List<InboxItem>>> = _inbox.asStateFlow()
    private val _conversations = MutableStateFlow(Loadable<List<CommsConversation>>())
    val conversations: StateFlow<Loadable<List<CommsConversation>>> = _conversations.asStateFlow()
    private val _agents = MutableStateFlow(Loadable<List<Agent>>())
    val agents: StateFlow<Loadable<List<Agent>>> = _agents.asStateFlow()
    private val _tail = MutableStateFlow(Loadable<List<TailEvent>>())
    val tail: StateFlow<Loadable<List<TailEvent>>> = _tail.asStateFlow()
    private val _agentActivity = MutableStateFlow<Pair<String, Loadable<List<ActivityItem>>>?>(null)
    val agentActivity: StateFlow<Pair<String, Loadable<List<ActivityItem>>>?> = _agentActivity.asStateFlow()

    // Home's cockpit: usage windows, fleet velocity, coordination, and assistant replies for Moving.
    private val _budgets = MutableStateFlow(Loadable<ServiceBudgets>())
    val budgets: StateFlow<Loadable<ServiceBudgets>> = _budgets.asStateFlow()
    private val _heartrate = MutableStateFlow(Loadable<Heartrate>())
    val heartrate: StateFlow<Loadable<Heartrate>> = _heartrate.asStateFlow()
    private val _fleet = MutableStateFlow(Loadable<FleetSnapshot>())
    val fleet: StateFlow<Loadable<FleetSnapshot>> = _fleet.asStateFlow()
    private val _replies = MutableStateFlow(Loadable<List<TailEvent>>())
    val replies: StateFlow<Loadable<List<TailEvent>>> = _replies.asStateFlow()

    private val _thread = MutableStateFlow<ThreadUi?>(null)
    val thread: StateFlow<ThreadUi?> = _thread.asStateFlow()

    private val _newSession = MutableStateFlow<NewSessionUi>(NewSessionUi.Idle)
    val newSession: StateFlow<NewSessionUi> = _newSession.asStateFlow()

    /** Ids of inbox items being decided, and per-item errors. */
    private val _inboxBusy = MutableStateFlow<Map<String, String?>>(emptyMap())
    val inboxBusy: StateFlow<Map<String, String?>> = _inboxBusy.asStateFlow()

    private var foreground = false
    private var reconnectJob: Job? = null
    private var tailJob: Job? = null
    private var tailVisible = false
    private var tailPaused = MutableStateFlow(false)
    val isTailPaused: StateFlow<Boolean> = tailPaused.asStateFlow()

    val isPaired: Boolean get() = identity.activeConnectionInfo() != null

    init {
        connection.onUnexpectedDisconnect = { scheduleReconnect() }
        viewModelScope.launch {
            connection.state.collect { state ->
                if (state is LinkState.Connected) {
                    refreshAll()
                    startTailLoopIfNeeded()
                }
            }
        }
        viewModelScope.launch {
            connection.events.collect { event ->
                when (event) {
                    is BridgeEvent.ConversationChanged -> onConversationChanged(event.conversationId)
                    is BridgeEvent.ConversationLifecycle -> {
                        _thread.update {
                            if (it?.conversationId == event.conversationId) it.copy(lifecycle = event.state) else it
                        }
                        if (event.state in setOf("completed", "failed", "replied")) onConversationChanged(event.conversationId)
                    }
                    is BridgeEvent.OperatorNotify -> refreshInbox()
                    BridgeEvent.SessionActivity -> Unit
                }
            }
        }
    }

    // -- Lifecycle ------------------------------------------------------------

    fun onForeground() {
        foreground = true
        if (isPaired && !connection.isConnected) scheduleReconnect(immediate = true)
        else if (connection.isConnected) refreshAll()
    }

    fun onBackground() {
        foreground = false
        reconnectJob?.cancel()
        // Like iOS, drop the socket while backgrounded; the relay and bridge
        // keep running on the computer, and we reconnect on return.
        connection.disconnect()
    }

    fun retryNow() = scheduleReconnect(immediate = true)

    private fun scheduleReconnect(immediate: Boolean = false) {
        if (!foreground || !isPaired) return
        if (reconnectJob?.isActive == true && !immediate) return
        reconnectJob?.cancel()
        reconnectJob = viewModelScope.launch {
            val backoff = listOf(0L, 1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L)
            var attempt = if (immediate) 0 else 1
            while (isActive && foreground && !connection.isConnected) {
                delay(backoff[attempt.coerceAtMost(backoff.lastIndex)])
                try {
                    connection.connect()
                    return@launch
                } catch (e: BridgeException) {
                    if (e.kind == FailureKind.NotPaired || e.kind == FailureKind.Untrusted) return@launch
                } catch (e: Exception) {
                    // keep trying
                }
                attempt++
            }
        }
    }

    // -- Pairing --------------------------------------------------------------

    fun pair(input: String) {
        val payload = try {
            QRPayload.parse(input)
        } catch (e: Exception) {
            _pairing.value = PairingUi.Failed("That doesn't look like a Scout pairing code. ${e.message ?: ""}".trim())
            return
        }
        payload.validate()?.let {
            _pairing.value = PairingUi.Failed(it)
            return
        }
        _pairing.value = PairingUi.Working("Establishing trust")
        viewModelScope.launch {
            try {
                connection.pair(payload)
                reloadMachines()
                _pairing.value = PairingUi.Idle
                foreground = true
            } catch (e: Exception) {
                _pairing.value = PairingUi.Failed(e.message ?: "Pairing failed.")
            }
        }
    }

    fun clearPairingError() {
        _pairing.value = PairingUi.Idle
    }

    fun switchMachine(publicKeyHex: String) {
        identity.setActivePublicKeyHex(publicKeyHex)
        reloadMachines()
        clearData()
        connection.disconnect()
        scheduleReconnect(immediate = true)
    }

    fun forgetMachine(publicKeyHex: String) {
        val wasActive = publicKeyHex.equals(_activeKey.value, ignoreCase = true)
        identity.forgetBridge(publicKeyHex)
        reloadMachines()
        if (wasActive) {
            connection.disconnect()
            clearData()
            if (isPaired) scheduleReconnect(immediate = true)
        }
    }

    fun renameMachine(publicKeyHex: String, name: String) {
        if (name.isBlank()) return
        identity.renameTrustedBridge(publicKeyHex, name.trim())
        reloadMachines()
    }

    private fun reloadMachines() {
        _machines.value = identity.trustedBridges()
        _activeKey.value = identity.activeConnectionInfo()?.publicKeyHex
    }

    private fun clearData() {
        _home.value = Loadable(); _activity.value = Loadable(); _inbox.value = Loadable()
        _conversations.value = Loadable(); _agents.value = Loadable(); _tail.value = Loadable()
        _budgets.value = Loadable(); _heartrate.value = Loadable(); _fleet.value = Loadable(); _replies.value = Loadable()
        _thread.value = null
    }

    // -- Data -----------------------------------------------------------------

    fun refreshAll() {
        refreshHome(); refreshActivity(); refreshInbox(); refreshConversations(); refreshAgents()
        refreshCockpit()
    }

    /** Side by side, like iOS: usage, velocity, fleet and replies each paint as they land. */
    fun refreshCockpit() {
        load(_budgets) { client.serviceBudgets() }
        load(_heartrate) { client.heartrate() }
        load(_fleet) { client.fleet(30) }
        load(_replies) { client.tailReplies(60) }
    }

    private fun friendly(e: Throwable): String = when (e) {
        is RpcException -> e.message.lineSequence().firstOrNull()?.take(160) ?: "Your computer returned an error."
        is BridgeException -> e.message ?: "Not connected."
        else -> e.message ?: e.javaClass.simpleName
    }

    private fun <T> load(target: MutableStateFlow<Loadable<T>>, block: suspend () -> T) {
        if (!connection.isConnected) return
        target.update { it.copy(loading = true) }
        viewModelScope.launch {
            try {
                val data = block()
                target.value = Loadable(data = data, updatedAt = System.currentTimeMillis())
            } catch (e: Exception) {
                target.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    fun refreshHome() = load(_home) { client.home() }
    fun refreshActivity() = load(_activity) { client.activity(40) }
    fun refreshInbox() = load(_inbox) { client.inbox() }
    fun refreshConversations() = load(_conversations) { client.conversations(100).sortedByDescending { it.lastMessageMs ?: 0 } }
    fun refreshAgents() = load(_agents) { client.agents(200) }

    fun loadAgentActivity(agentId: String) {
        if (!connection.isConnected) return
        _agentActivity.value = agentId to Loadable(loading = true)
        viewModelScope.launch {
            _agentActivity.value = try {
                agentId to Loadable(client.activity(30, agentId), updatedAt = System.currentTimeMillis())
            } catch (e: Exception) {
                agentId to Loadable(error = friendly(e))
            }
        }
    }

    private var changeDebounce: Job? = null
    private fun onConversationChanged(conversationId: String) {
        val open = _thread.value
        if (open?.conversationId == conversationId) reloadThread()
        changeDebounce?.cancel()
        changeDebounce = viewModelScope.launch {
            delay(400)
            refreshConversations(); refreshActivity(); load(_fleet) { client.fleet(30) }
        }
    }

    // -- Tail -----------------------------------------------------------------

    fun setTailVisible(visible: Boolean) {
        tailVisible = visible
        if (visible) startTailLoopIfNeeded() else tailJob?.cancel()
    }

    fun setTailPaused(paused: Boolean) {
        tailPaused.value = paused
        if (!paused) startTailLoopIfNeeded()
    }

    private fun startTailLoopIfNeeded() {
        if (!tailVisible || tailJob?.isActive == true) return
        tailJob = viewModelScope.launch {
            while (isActive && tailVisible) {
                if (connection.isConnected && !tailPaused.value) {
                    try {
                        val events = client.tail(150).sortedByDescending { it.tsMs }
                        _tail.value = Loadable(events, updatedAt = System.currentTimeMillis())
                    } catch (e: Exception) {
                        _tail.update { it.copy(loading = false, error = friendly(e)) }
                    }
                }
                delay(3_000)
            }
        }
    }

    // -- Threads --------------------------------------------------------------

    fun openThread(conversationId: String?, title: String, agentId: String?) {
        val current = _thread.value
        if (current != null && current.conversationId == conversationId && conversationId != null) {
            reloadThread(); return
        }
        _thread.value = ThreadUi(
            conversationId = conversationId,
            title = title,
            agentId = agentId,
            messages = if (conversationId == null) Loadable(data = emptyList()) else Loadable(loading = true),
        )
        if (conversationId != null) reloadThread()
    }

    fun closeThread() {
        _thread.value = null
    }

    fun reloadThread() {
        val t = _thread.value ?: return
        val id = t.conversationId ?: return
        if (!connection.isConnected) return
        viewModelScope.launch {
            try {
                val messages = client.messages(id, 200).sortedBy { it.createdMs }
                _thread.update { cur ->
                    if (cur?.conversationId != id) cur
                    else cur.copy(
                        messages = Loadable(messages, updatedAt = System.currentTimeMillis()),
                        pending = cur.pending.filterNot { p -> messages.any { m -> m.clientMessageId != null && m.clientMessageId == p.clientMessageId || (m.fromOperator && m.body == p.body) } },
                    )
                }
                messages.lastOrNull()?.let { last ->
                    runCatching { client.markRead(id, last.id) }
                }
            } catch (e: Exception) {
                _thread.update { cur -> if (cur?.conversationId != id) cur else cur.copy(messages = cur.messages.copy(loading = false, error = friendly(e))) }
            }
        }
    }

    fun send(body: String) {
        val t = _thread.value ?: return
        val text = body.trim()
        if (text.isEmpty()) return
        val local = CommsMessage(
            id = "local-${System.nanoTime()}",
            conversationId = t.conversationId ?: "",
            actorId = "operator",
            authorLabel = "You",
            authorKind = "operator",
            body = text,
            createdAt = System.currentTimeMillis(),
            isOperator = true,
            clientMessageId = null,
        )
        _thread.update { it?.copy(pending = it.pending + local, sendError = null) }
        viewModelScope.launch {
            try {
                if (t.conversationId != null) {
                    val result = client.postMessage(t.conversationId, text)
                    result.delivery?.takeIf { it.state != "delivered" && it.state != "accepted" && it.state != "queued" }?.let { d ->
                        _thread.update { it?.copy(sendError = d.detail ?: "Delivery: ${d.state}") }
                    }
                    reloadThread()
                } else if (t.agentId != null) {
                    val result = client.sendDirectMessage(t.agentId, text)
                    _thread.update { it?.copy(conversationId = result.conversationId) }
                    reloadThread()
                }
                refreshConversations()
            } catch (e: Exception) {
                _thread.update { it?.copy(pending = it.pending.filterNot { p -> p.id == local.id }, sendError = friendly(e)) }
            }
        }
    }

    // -- Inbox actions ----------------------------------------------------------

    private fun inboxAction(item: InboxItem, block: suspend () -> Unit) {
        _inboxBusy.update { it + (item.id to null) }
        viewModelScope.launch {
            try {
                block()
                _inboxBusy.update { it - item.id }
                refreshInbox()
            } catch (e: Exception) {
                _inboxBusy.update { it + (item.id to "Couldn't send that. ${friendly(e)}") }
            }
        }
    }

    fun decide(item: InboxItem, approve: Boolean) = inboxAction(item) { client.decideAction(item, approve) }
    fun answer(item: InboxItem, answer: String) = inboxAction(item) { client.answerQuestion(item, answer) }
    fun replyToAsk(item: InboxItem, text: String) = inboxAction(item) { client.postMessage(item.sessionId, text) }

    fun interruptAgentSafely(agentId: String) {
        viewModelScope.launch {
            runCatching { client.interruptAgent(agentId) }
            refreshAgents()
        }
    }

    // -- New session ------------------------------------------------------------

    fun startSession(workspaceId: String, harness: String?, instructions: String) {
        _newSession.value = NewSessionUi.Starting
        viewModelScope.launch {
            _newSession.value = try {
                val handle = client.createSession(workspaceId, harness, instructions)
                refreshConversations(); refreshAgents(); refreshHome()
                NewSessionUi.Started(handle.session.conversationId, handle.session.title.ifBlank { handle.agent.title }, handle.agent.id)
            } catch (e: Exception) {
                NewSessionUi.Failed(friendly(e))
            }
        }
    }

    fun resetNewSession() {
        _newSession.value = NewSessionUi.Idle
    }
}
