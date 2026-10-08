// Bridge connection lifecycle — the Android counterpart of BridgeConnection.swift.
//
//   - pair(): first-time pairing from a QR/link payload (Noise XX), trusting the
//     bridge only when the learned static key matches the payload's key;
//   - connect(): reconnect to the trusted bridge (Noise IK), resolving the
//     bridge's current room through the relay's POST /resolve first;
//   - rpc: tRPC JSON-RPC envelopes over the encrypted channel with a pending map
//     keyed by request id and a per-request timeout;
//   - pushed frames (conversation invalidations, operator notifications) are
//     fanned out as BridgeEvents.
//
// The app never talks to the broker directly: everything goes through the
// paired bridge's tRPC router, exactly like iOS.

package app.openscout.scout.core.bridge

import app.openscout.scout.core.crypto.NoiseKeyPair
import app.openscout.scout.core.crypto.toHex
import app.openscout.scout.core.identity.BridgeConnectionInfo
import app.openscout.scout.core.identity.IdentityStore
import app.openscout.scout.core.pairing.QRPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

sealed interface LinkState {
    data object Idle : LinkState
    data class Connecting(val detail: String) : LinkState
    data class Connected(val relayUrl: String, val route: RouteKind) : LinkState
    data class Failed(val message: String, val kind: FailureKind) : LinkState
}

enum class FailureKind { NotPaired, Unreachable, BridgeOffline, Untrusted, Protocol }

enum class RouteKind(val label: String) {
    Emulator("Emulator host"),
    Loopback("Loopback"),
    Lan("Local network"),
    Tailnet("Tailnet"),
    Remote("Relay");

    companion object {
        fun of(relayUrl: String): RouteKind {
            val host = runCatching { URI(relayUrl).host }.getOrNull()?.lowercase() ?: return Remote
            return when {
                host == "10.0.2.2" -> Emulator
                host == "localhost" || host == "127.0.0.1" -> Loopback
                host.endsWith(".ts.net") || host.startsWith("100.") -> Tailnet
                host.startsWith("192.168.") || host.startsWith("10.") || host.endsWith(".local") ||
                    Regex("^172\\.(1[6-9]|2\\d|3[01])\\.").containsMatchIn(host) -> Lan
                else -> Remote
            }
        }
    }
}

sealed interface BridgeEvent {
    data class ConversationChanged(val conversationId: String, val messageId: String?) : BridgeEvent
    data class ConversationLifecycle(val conversationId: String, val state: String?, val summary: String?) : BridgeEvent
    /** An agent needs the operator; [item] is the `mobile.inbox` row the bridge attached. */
    data class OperatorNotify(val tier: String?, val title: String?, val item: JsonObject? = null) : BridgeEvent
    data object SessionActivity : BridgeEvent
}

class RpcException(val code: Int, override val message: String) : Exception(message)
class BridgeException(val kind: FailureKind, message: String) : Exception(message)

data class LogLine(val at: Long, val text: String, val level: Level) {
    enum class Level { Info, Success, Warning, Error }
    val clock: String get() = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(at))
}

class BridgeConnection(
    private val identity: IdentityStore,
    private val scope: CoroutineScope,
    private val isEmulator: Boolean,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private val http = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        // WS-level pings keep relays/NATs from reaping the idle socket; a missed
        // pong fails the socket, which surfaces as a disconnect and a reconnect.
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    private val resolveHttp = http.newBuilder()
        .callTimeout(3, TimeUnit.SECONDS)
        .pingInterval(0, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow<LinkState>(LinkState.Idle)
    val state: StateFlow<LinkState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<BridgeEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<BridgeEvent> = _events.asSharedFlow()

    private val _log = MutableStateFlow<List<LogLine>>(emptyList())
    val log: StateFlow<List<LogLine>> = _log.asStateFlow()

    /** Fired (on the connection scope) when an established link drops on its own. */
    var onUnexpectedDisconnect: ((String) -> Unit)? = null

    private val lifecycle = Mutex()
    private val generation = AtomicInteger(0)
    private val nextRequestId = AtomicInteger(1)
    private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonElement>>()

    @Volatile private var channel: SecureChannel? = null
    private var readerJob: Job? = null

    val isConnected: Boolean get() = channel != null && _state.value is LinkState.Connected

    // -- Logging --------------------------------------------------------------

    fun log(text: String, level: LogLine.Level = LogLine.Level.Info) {
        _log.update { (it + LogLine(System.currentTimeMillis(), text, level)).takeLast(200) }
    }

    // -- Lifecycle ------------------------------------------------------------

    suspend fun pair(payload: QRPayload) = lifecycle.withLock {
        withContext(Dispatchers.IO) {
            payload.validate()?.let { reason ->
                log("Invalid pairing payload: $reason", LogLine.Level.Error)
                throw BridgeException(FailureKind.Protocol, reason)
            }
            teardown()
            val gen = generation.incrementAndGet()
            val keyPair = identity.loadOrCreateIdentity()
            val candidates = withEmulatorRoutes(payload.orderedRelayUrls)
            log("Pairing (Noise XX) room=${payload.room.take(8)}… via ${candidates.size} route(s)")
            _state.value = LinkState.Connecting("Establishing trust")

            val attempt = tryCandidates(candidates, keyPair, remoteStaticKey = null, expectedKey = payload.bridgePublicKey) { _ ->
                payload.room
            }
            val keyHex = attempt.channel.remoteStaticKey.toHex()
            identity.saveTrustedBridge(keyHex, machineNameFrom(payload.orderedRelayUrls))
            val ordered = promote(attempt.relayUrl, candidates)
            identity.saveConnectionInfo(
                BridgeConnectionInfo(
                    publicKeyHex = keyHex,
                    relayUrl = ordered.first(),
                    roomId = attempt.roomId,
                    fallbackRelayUrls = ordered.drop(1),
                    webPort = payload.webPort,
                ),
            )
            activate(attempt, gen)
            log("Paired via ${RouteKind.of(attempt.relayUrl).label} ${attempt.relayUrl}", LogLine.Level.Success)
        }
    }

    suspend fun connect() = lifecycle.withLock {
        withContext(Dispatchers.IO) {
            if (channel != null && _state.value is LinkState.Connected) return@withContext
            val info = identity.activeConnectionInfo()
            if (info == null) {
                _state.value = LinkState.Failed("Pair with Scout on your computer to get started.", FailureKind.NotPaired)
                throw BridgeException(FailureKind.NotPaired, "No paired computer")
            }
            if (!identity.isTrusted(info.publicKeyHex)) {
                _state.value = LinkState.Failed("This computer is no longer trusted. Pair again.", FailureKind.Untrusted)
                throw BridgeException(FailureKind.Untrusted, "Bridge identity is no longer trusted")
            }
            teardown()
            val gen = generation.incrementAndGet()
            val keyPair = identity.loadOrCreateIdentity()
            val expected = info.publicKeyHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val candidates = withEmulatorRoutes(info.orderedRelayUrls)
            _state.value = LinkState.Connecting("Reaching your computer")
            log("Connecting (Noise IK) via ${candidates.size} route(s)")

            val attempt = try {
                tryCandidates(candidates, keyPair, remoteStaticKey = expected, expectedKey = expected) { relayUrl ->
                    when (val r = resolveRoom(relayUrl, info.publicKeyHex)) {
                        is ResolveResult.Resolved -> r.room.also {
                            if (it != info.roomId) log("Resolved current room on ${RouteKind.of(relayUrl).label}")
                        }
                        ResolveResult.BridgeOffline -> throw BridgeException(FailureKind.BridgeOffline, "Scout's bridge is not in the relay room")
                        ResolveResult.Unavailable -> info.roomId
                    }
                }
            } catch (e: BridgeException) {
                _state.value = LinkState.Failed(e.message ?: "Unreachable", e.kind)
                throw e
            }
            val ordered = promote(attempt.relayUrl, candidates)
            identity.saveConnectionInfo(
                info.copy(relayUrl = ordered.first(), roomId = attempt.roomId, fallbackRelayUrls = ordered.drop(1)),
            )
            identity.touchTrustedBridge(info.publicKeyHex)
            activate(attempt, gen)
            log("Connected via ${RouteKind.of(attempt.relayUrl).label} ${attempt.relayUrl}", LogLine.Level.Success)
        }
    }

    fun disconnect() {
        generation.incrementAndGet()
        teardown()
        _state.value = LinkState.Idle
    }

    private class Attempt(val relayUrl: String, val roomId: String, val channel: SecureChannel)

    private suspend fun tryCandidates(
        candidates: List<String>,
        keyPair: NoiseKeyPair,
        remoteStaticKey: ByteArray?,
        expectedKey: ByteArray,
        roomFor: suspend (String) -> String,
    ): Attempt {
        if (candidates.isEmpty()) throw BridgeException(FailureKind.Unreachable, "No relay routes are available")
        var bridgeOffline = false
        var lastError: String? = null
        for (relayUrl in candidates) {
            val route = RouteKind.of(relayUrl)
            val room = try {
                roomFor(relayUrl)
            } catch (e: BridgeException) {
                if (e.kind == FailureKind.BridgeOffline) bridgeOffline = true
                log("${route.label}: ${e.message}", LogLine.Level.Warning)
                continue
            }
            val url = buildRelayUrl(relayUrl, room) ?: continue
            _state.value = LinkState.Connecting("Trying ${route.label.lowercase()}")
            var socket: RelaySocket? = null
            try {
                socket = RelaySocket.open(http, url, timeoutMs = 4_000)
                val secure = SecureChannel.handshake(socket, keyPair, remoteStaticKey, timeoutMs = 6_000)
                if (!secure.remoteStaticKey.contentEquals(expectedKey)) {
                    socket.close()
                    log("${route.label}: bridge key mismatch — refusing", LogLine.Level.Error)
                    throw BridgeException(FailureKind.Untrusted, "The computer's identity didn't match the pairing code.")
                }
                return Attempt(relayUrl, room, secure)
            } catch (e: BridgeException) {
                throw e
            } catch (e: Exception) {
                socket?.close()
                lastError = e.message ?: e.javaClass.simpleName
                log("${route.label} $relayUrl failed: $lastError", LogLine.Level.Warning)
            }
        }
        if (bridgeOffline) {
            throw BridgeException(FailureKind.BridgeOffline, "Your computer's relay is up, but Scout's bridge isn't connected to it.")
        }
        throw BridgeException(FailureKind.Unreachable, "Couldn't reach your computer on any route${lastError?.let { " ($it)" } ?: ""}.")
    }

    private fun activate(attempt: Attempt, gen: Int) {
        channel = attempt.channel
        _state.value = LinkState.Connected(attempt.relayUrl, RouteKind.of(attempt.relayUrl))
        readerJob = scope.launch(Dispatchers.IO) {
            val secure = attempt.channel
            for (raw in secure.socket.incoming) {
                val text = try {
                    secure.decryptFrame(raw)
                } catch (e: Exception) {
                    log("Decrypt failed; bridge rolled keys", LogLine.Level.Warning)
                    break
                } ?: continue
                handleMessage(text, secure)
            }
            val reason = if (secure.socket.closed.isCompleted) secure.socket.closed.await() else "transport ended"
            markDisconnected(gen, reason)
        }
    }

    private fun markDisconnected(gen: Int, reason: String) {
        if (gen != generation.get()) return
        val hadChannel = channel != null
        teardown()
        if (hadChannel) {
            log("Connection dropped: $reason", LogLine.Level.Warning)
            _state.value = LinkState.Failed("Connection to your computer dropped.", FailureKind.Unreachable)
            onUnexpectedDisconnect?.invoke(reason)
        }
    }

    private fun teardown() {
        readerJob?.cancel()
        readerJob = null
        channel?.socket?.close()
        channel = null
        val err = BridgeException(FailureKind.Unreachable, "Not connected to your computer.")
        pending.values.forEach { it.completeExceptionally(err) }
        pending.clear()
    }

    // -- Inbound ----------------------------------------------------------------

    private fun handleMessage(text: String, secure: SecureChannel) {
        if (text == "PING") {
            secure.send("PONG"); return
        }
        if (text == "PONG") return
        val obj = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull() ?: return
        val id = (obj["id"] as? JsonPrimitive)?.intOrNull
        if (id != null) {
            val deferred = pending.remove(id) ?: return
            val error = obj["error"] as? JsonObject
            if (error != null) {
                val code = (error["code"] as? JsonPrimitive)?.intOrNull ?: -1
                val message = (error["message"] as? JsonPrimitive)?.contentOrNull ?: "Bridge error"
                deferred.completeExceptionally(RpcException(code, message))
            } else {
                val data = (obj["result"] as? JsonObject)?.get("data") ?: JsonNull
                deferred.complete(data)
            }
            return
        }
        when ((obj["event"] as? JsonPrimitive)?.contentOrNull) {
            "mobile:conversation:changed" -> _events.tryEmit(
                BridgeEvent.ConversationChanged(obj.str("conversationId") ?: return, obj.str("messageId")),
            )
            "mobile:conversation:lifecycle" -> _events.tryEmit(
                BridgeEvent.ConversationLifecycle(obj.str("conversationId") ?: return, obj.str("lifecycleState"), obj.str("summary")),
            )
            "operator:notify" -> _events.tryEmit(
                BridgeEvent.OperatorNotify(obj.str("tier"), (obj["item"] as? JsonObject)?.str("title"), obj["item"] as? JsonObject),
            )
            else -> if (obj.containsKey("seq")) _events.tryEmit(BridgeEvent.SessionActivity)
        }
    }

    private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

    // -- RPC ------------------------------------------------------------------

    suspend fun query(path: String, input: JsonElement? = null): JsonElement = call("query", path, input)

    suspend fun mutation(path: String, input: JsonElement? = null, timeoutMs: Long = RPC_TIMEOUT_MS): JsonElement =
        call("mutation", path, input, timeoutMs)

    private suspend fun call(method: String, path: String, input: JsonElement?, timeoutMs: Long = RPC_TIMEOUT_MS): JsonElement {
        val secure = channel ?: throw BridgeException(FailureKind.Unreachable, "Not connected to your computer.")
        val id = nextRequestId.getAndIncrement()
        val envelope = buildJsonObject {
            put("id", id)
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", buildJsonObject {
                put("path", path)
                if (input != null) put("input", input)
            })
        }
        val deferred = CompletableDeferred<JsonElement>()
        pending[id] = deferred
        if (!secure.send(envelope.toString())) {
            pending.remove(id)
            throw BridgeException(FailureKind.Unreachable, "Not connected to your computer.")
        }
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            pending.remove(id)
            throw BridgeException(FailureKind.Unreachable, "Your computer didn't answer ($path).")
        }
    }

    // -- Routes ---------------------------------------------------------------

    private sealed interface ResolveResult {
        data class Resolved(val room: String) : ResolveResult
        data object BridgeOffline : ResolveResult
        data object Unavailable : ResolveResult
    }

    private fun resolveRoom(relayUrl: String, publicKeyHex: String): ResolveResult {
        val httpUrl = relayUrl.replaceFirst("wss://", "https://").replaceFirst("ws://", "http://").trimEnd('/') + "/resolve"
        val body = """{"bridgePublicKey":"$publicKeyHex"}""".toRequestBody("application/json".toMediaType())
        return try {
            resolveHttp.newCall(Request.Builder().url(httpUrl).post(body).build()).execute().use { res ->
                when (res.code) {
                    200 -> {
                        val room = runCatching {
                            json.parseToJsonElement(res.body.string()).jsonObject["room"]?.jsonPrimitive?.contentOrNull
                        }.getOrNull()?.trim()
                        if (room.isNullOrEmpty()) ResolveResult.Unavailable else ResolveResult.Resolved(room)
                    }
                    404 -> ResolveResult.BridgeOffline
                    else -> ResolveResult.Unavailable
                }
            }
        } catch (e: Exception) {
            ResolveResult.Unavailable
        }
    }

    private fun buildRelayUrl(relayUrl: String, room: String): String? = runCatching {
        val base = relayUrl.trim().substringBefore('?').trimEnd('/')
        "$base?room=${java.net.URLEncoder.encode(room, "UTF-8")}&role=client"
    }.getOrNull()

    /**
     * Inside the Android emulator the host machine is 10.0.2.2. The advertised
     * LAN/tailnet relays are tried first (they are NATed through the emulator and
     * usually work); the alias is a fallback for hosts whose advertised address
     * the emulator can't route. On recent emulator images the app's default
     * (virtual Wi-Fi) network may not reach 10.0.2.2 at all, so it must not go
     * first. Physical devices never see this route.
     */
    private fun withEmulatorRoutes(urls: List<String>): List<String> {
        if (!isEmulator) return urls
        val aliases = urls.mapNotNull { url ->
            runCatching {
                val uri = URI(url)
                if (uri.host == "10.0.2.2") null
                else "${uri.scheme}://10.0.2.2:${if (uri.port > 0) uri.port else 43131}"
            }.getOrNull()
        }
        return (urls + aliases).distinct()
    }

    private fun promote(winner: String, all: List<String>): List<String> = listOf(winner) + all.filter { it != winner }

    /** A human label for a newly paired computer: the first DNS (non-IP) relay host's first label. */
    private fun machineNameFrom(urls: List<String>): String? = urls.firstNotNullOfOrNull { url ->
        val host = runCatching { URI(url).host }.getOrNull() ?: return@firstNotNullOfOrNull null
        if (host.isBlank() || host.first().isDigit() || host.contains(':') || host == "localhost") null
        else if (host.endsWith("oscout.net")) null
        else host.substringBefore('.')
    }

    companion object {
        const val RPC_TIMEOUT_MS = 20_000L
        const val CREATE_SESSION_TIMEOUT_MS = 60_000L
    }
}
