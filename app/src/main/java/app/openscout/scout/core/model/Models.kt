// Wire shapes for the bridge's mobile.* tRPC procedures
// (packages/web/server/core/pairing/runtime/bridge/router.ts). Field names
// match the iOS decoders in BridgeBrokerClient.swift; everything optional is
// nullable so an older or newer computer never fails a whole list.

package app.openscout.scout.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Broker timestamps are usually epoch ms, but some records carry seconds. */
fun epochMillis(value: Long?): Long? = when {
    value == null || value <= 0 -> null
    value < 100_000_000_000L -> value * 1000
    else -> value
}

@Serializable
data class WorkspaceHarness(
    val harness: String,
    val source: String? = null,
    val detail: String? = null,
    val readinessState: String? = null,
    val readinessDetail: String? = null,
)

@Serializable
data class Workspace(
    val id: String,
    val title: String? = null,
    val projectName: String? = null,
    val root: String? = null,
    val defaultHarness: String? = null,
    val harnesses: List<WorkspaceHarness> = emptyList(),
    val branch: String? = null,
    val isWorktree: Boolean? = null,
) {
    val displayName: String
        get() = title?.takeIf { it.isNotBlank() } ?: projectName?.takeIf { it.isNotBlank() }
            ?: root?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: id
    val readyHarnesses: List<WorkspaceHarness> get() = harnesses.filter { it.readinessState == "ready" }
}

@Serializable
data class Agent(
    val id: String,
    val title: String,
    val selector: String? = null,
    val defaultSelector: String? = null,
    val nodeId: String? = null,
    val nodeName: String? = null,
    val workspaceRoot: String? = null,
    val harness: String? = null,
    val transport: String? = null,
    val state: String = "unknown",
    val statusLabel: String? = null,
    val sessionId: String? = null,
    val conversationId: String? = null,
    val lastActiveAt: Long? = null,
    val needsAttention: Boolean? = null,
    val pendingAsk: JsonElement? = null,
) {
    val projectName: String? get() = workspaceRoot?.trimEnd('/')?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
    val pendingAskText: String?
        get() = (pendingAsk as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    val attention: Boolean
        get() = needsAttention == true || state.equals("needs_attention", true) || pendingAskText != null
    val liveness: Liveness
        get() = when (state.lowercase()) {
            "working", "live", "active", "online" -> Liveness.Live
            "available", "idle", "waiting" -> Liveness.Idle
            "offline" -> Liveness.Offline
            else -> Liveness.Unknown
        }
    val lastActiveMs: Long? get() = epochMillis(lastActiveAt)

    enum class Liveness { Live, Idle, Offline, Unknown }
}

@Serializable
data class SessionSummary(
    val id: String,
    val kind: String = "direct",
    val title: String = "",
    val participantIds: List<String>? = null,
    val agentId: String? = null,
    val agentName: String? = null,
    val harness: String? = null,
    val currentBranch: String? = null,
    val preview: String? = null,
    val messageCount: Int? = null,
    val lastMessageAt: Long? = null,
    val workspaceRoot: String? = null,
)

@Serializable
data class HomeTotals(val workspaces: Int = 0, val agents: Int = 0, val sessions: Int = 0)

@Serializable
data class MobileHome(
    val workspaces: List<Workspace> = emptyList(),
    val agents: List<Agent> = emptyList(),
    val sessions: List<SessionSummary> = emptyList(),
    val totals: HomeTotals? = null,
)

@Serializable
data class ActivityItem(
    val id: String,
    val kind: String = "message",
    val actorId: String = "",
    val actorName: String = "",
    val title: String = "",
    val detail: String? = null,
    val conversationId: String? = null,
    val channel: String? = null,
    val timestamp: Long = 0,
    val harness: String? = null,
) {
    val timestampMs: Long get() = epochMillis(timestamp) ?: 0
}

@Serializable
data class TailEvent(
    val id: String,
    val ts: Long = 0,
    val source: String = "",
    val sessionId: String = "",
    val pid: Long? = null,
    val parentPid: Long? = null,
    val project: String = "",
    val cwd: String = "",
    val harness: String = "",
    val kind: String = "other",
    val summary: String = "",
) {
    val tsMs: Long get() = epochMillis(ts) ?: 0
}

@Serializable
data class CommsConversation(
    val id: String,
    val kind: String = "direct",
    val title: String = "",
    val participants: List<String>? = null,
    val topic: String? = null,
    val lastMessagePreview: String? = null,
    val lastMessageAuthor: String? = null,
    val lastMessageAt: Long? = null,
    val messageCount: Int? = null,
    val unreadCount: Int? = null,
) {
    val lastMessageMs: Long? get() = epochMillis(lastMessageAt)
}

@Serializable
data class MessageAttachment(
    val id: String = "",
    val mediaType: String = "",
    val fileName: String? = null,
    val blobKey: String? = null,
    val url: String? = null,
)

@Serializable
data class CommsMessage(
    val id: String,
    val conversationId: String = "",
    val actorId: String = "",
    val authorLabel: String = "",
    val authorKind: String = "unknown",
    val body: String = "",
    val createdAt: Long = 0,
    val replyToMessageId: String? = null,
    val isOperator: Boolean? = null,
    val attachments: List<MessageAttachment>? = null,
    val clientMessageId: String? = null,
) {
    val fromOperator: Boolean get() = isOperator ?: (actorId == "operator")
    val createdMs: Long get() = epochMillis(createdAt) ?: 0
}

@Serializable
data class DeliveryState(val state: String, val reason: String? = null, val action: String? = null, val detail: String? = null)

@Serializable
data class CommsSendResult(
    val conversationId: String,
    val messageId: String,
    val flightId: String? = null,
    val invocationId: String? = null,
    val targetAgentId: String? = null,
    val lifecycleState: String? = null,
    val summary: String? = null,
    val delivery: DeliveryState? = null,
)

@Serializable
data class MessageSendFlight(
    val id: String = "",
    val invocationId: String? = null,
    val targetAgentId: String? = null,
    val state: String? = null,
    val summary: String? = null,
    val error: String? = null,
)

@Serializable
data class MessageSendResult(
    val conversationId: String,
    val messageId: String,
    val flight: MessageSendFlight? = null,
)

@Serializable
data class InboxItem(
    val id: String,
    val kind: String = "",
    val createdAt: Long = 0,
    val sessionId: String = "",
    val sessionName: String = "",
    val adapterType: String = "",
    val turnId: String? = null,
    val blockId: String? = null,
    val version: Int? = null,
    val risk: String = "low",
    val title: String = "",
    val description: String = "",
    val detail: String? = null,
    val actionKind: String? = null,
    val actionStatus: String? = null,
    val options: List<String>? = null,
) {
    val createdMs: Long get() = epochMillis(createdAt) ?: 0
    val isDecidableApproval: Boolean get() = kind == "approval" && turnId != null && blockId != null && version != null
    val isAnswerableQuestion: Boolean get() = kind == "question" && turnId != null && blockId != null
    val isConversationAsk: Boolean get() = kind == "ask" && sessionId.isNotEmpty()
    val isTerminalPrompt: Boolean get() = id.startsWith("herdr-")
}

@Serializable
data class Inbox(val items: List<InboxItem> = emptyList())

@Serializable
data class FleetWorkItem(
    val invocationId: String,
    val agentId: String = "",
    val agentName: String? = null,
    val conversationId: String? = null,
    val task: String = "",
    val status: String = "",
    val statusLabel: String = "",
    val harness: String? = null,
    val transport: String? = null,
    val summary: String? = null,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val updatedAt: Long = 0,
) {
    val updatedMs: Long get() = epochMillis(updatedAt) ?: 0
}

@Serializable
data class FleetSnapshot(
    val activeAsks: List<FleetWorkItem> = emptyList(),
    val recentCompleted: List<FleetWorkItem> = emptyList(),
)

@Serializable
data class SessionHandleAgent(val id: String, val title: String = "")

@Serializable
data class SessionHandleConversation(val conversationId: String, val title: String = "", val existed: Boolean = false)

@Serializable
data class SessionHandle(
    val agent: SessionHandleAgent,
    val session: SessionHandleConversation,
    val messageId: String? = null,
    val flightId: String? = null,
)
