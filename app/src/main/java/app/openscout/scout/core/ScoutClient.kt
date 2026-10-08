// Typed calls over the paired bridge. Procedure paths match the iOS route map
// (packages/scout-ios-core/Sources/ScoutIOSCore/RPCWire.swift).

package app.openscout.scout.core

import app.openscout.scout.core.bridge.BridgeConnection
import app.openscout.scout.core.model.ActivityItem
import app.openscout.scout.core.model.Agent
import app.openscout.scout.core.model.CommsConversation
import app.openscout.scout.core.model.CommsMessage
import app.openscout.scout.core.model.CommsSendResult
import app.openscout.scout.core.model.FleetSnapshot
import app.openscout.scout.core.model.Inbox
import app.openscout.scout.core.model.InboxItem
import app.openscout.scout.core.model.MessageSendResult
import app.openscout.scout.core.model.MobileHome
import app.openscout.scout.core.model.SessionHandle
import app.openscout.scout.core.model.TailEvent
import app.openscout.scout.core.model.Workspace
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

class ScoutClient(private val connection: BridgeConnection) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

    private fun <T> decode(serializer: KSerializer<T>, element: JsonElement): T = json.decodeFromJsonElement(serializer, element)

    private fun obj(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(builder)

    suspend fun home(): MobileHome =
        decode(MobileHome.serializer(), connection.query("mobile.home", obj { put("agentLimit", 50); put("sessionLimit", 30) }))

    suspend fun agents(limit: Int = 100): List<Agent> =
        decode(ListSerializer(Agent.serializer()), connection.query("mobile.agents", obj { put("limit", limit) }))

    suspend fun workspaces(limit: Int = 100): List<Workspace> =
        decode(ListSerializer(Workspace.serializer()), connection.query("mobile.workspaces", obj { put("limit", limit) }))

    suspend fun activity(limit: Int = 40, agentId: String? = null): List<ActivityItem> =
        decode(
            ListSerializer(ActivityItem.serializer()),
            connection.query("mobile.activity", obj { put("limit", limit); agentId?.let { put("agentId", it) } }),
        )

    suspend fun fleet(limit: Int = 30): FleetSnapshot =
        decode(FleetSnapshot.serializer(), connection.query("mobile.fleet", obj { put("limit", limit) }))

    suspend fun tail(limit: Int = 120): List<TailEvent> =
        decode(ListSerializer(TailEvent.serializer()), connection.query("mobile.tail", obj { put("limit", limit) }))

    suspend fun inbox(): List<InboxItem> = decode(Inbox.serializer(), connection.query("mobile.inbox")).items

    suspend fun conversations(limit: Int = 100): List<CommsConversation> =
        decode(ListSerializer(CommsConversation.serializer()), connection.query("mobile.commsConversations", obj { put("limit", limit) }))

    suspend fun messages(conversationId: String, limit: Int = 200): List<CommsMessage> =
        decode(
            ListSerializer(CommsMessage.serializer()),
            connection.query("mobile.commsMessages", obj { put("conversationId", conversationId); put("limit", limit) }),
        )

    suspend fun postMessage(conversationId: String, body: String): CommsSendResult =
        decode(
            CommsSendResult.serializer(),
            connection.mutation("mobile.commsSend", obj {
                put("conversationId", conversationId)
                put("body", body)
                put("clientMessageId", UUID.randomUUID().toString())
            }),
        )

    /** Direct message to an agent; the broker creates or reuses the DM conversation. */
    suspend fun sendDirectMessage(agentId: String, body: String): MessageSendResult =
        decode(
            MessageSendResult.serializer(),
            connection.mutation("mobile.sendMessage", obj {
                put("agentId", agentId)
                put("body", body)
                put("clientMessageId", UUID.randomUUID().toString())
            }),
        )

    suspend fun markRead(conversationId: String, lastReadMessageId: String?) {
        connection.mutation("mobile.commsMarkRead", obj {
            put("conversationId", conversationId)
            lastReadMessageId?.let { put("lastReadMessageId", it) }
        })
    }

    suspend fun createSession(workspaceId: String, harness: String?, instructions: String?): SessionHandle =
        decode(
            SessionHandle.serializer(),
            connection.mutation(
                "mobile.createSession",
                obj {
                    put("workspaceId", workspaceId)
                    harness?.let { put("harness", it) }
                    if (!instructions.isNullOrBlank()) put("seed", obj { put("instructions", instructions) })
                },
                timeoutMs = BridgeConnection.CREATE_SESSION_TIMEOUT_MS,
            ),
        )

    suspend fun decideAction(item: InboxItem, approve: Boolean) {
        connection.mutation("actionDecide", obj {
            put("sessionId", item.sessionId)
            put("turnId", item.turnId ?: "")
            put("blockId", item.blockId ?: "")
            put("version", item.version ?: 0)
            put("decision", if (approve) "approve" else "deny")
        })
    }

    suspend fun answerQuestion(item: InboxItem, answer: String) {
        connection.mutation("questionAnswer", obj {
            put("sessionId", item.sessionId)
            put("blockId", item.blockId ?: "")
            put("answer", buildJsonArray { add(kotlinx.serialization.json.JsonPrimitive(answer)) })
        })
    }

    suspend fun interruptAgent(agentId: String) {
        connection.mutation("mobile.agentInterrupt", obj { put("agentId", agentId) })
    }
}
