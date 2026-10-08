// Encrypted relay socket — wire-compatible with SecureTransport.swift and
// packages/runtime/src/pairing-security/transport.ts.
//
// Wire format (JSON text frames):
//   handshake: { "phase": "handshake", "payload": "<base64>" }
//   transport: { "phase": "transport", "payload": "<base64>" }

package app.openscout.scout.core.bridge

import app.openscout.scout.core.crypto.HandshakePattern
import app.openscout.scout.core.crypto.NoiseHandshake
import app.openscout.scout.core.crypto.NoiseKeyPair
import app.openscout.scout.core.crypto.NoiseRole
import app.openscout.scout.core.crypto.NoiseSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.util.Base64

@Serializable
internal data class WireMessage(val phase: String, val payload: String)

private val wireJson = Json { ignoreUnknownKeys = true }

class TransportException(message: String, cause: Throwable? = null) : Exception(message, cause)

/** One OkHttp WebSocket with its frames exposed as a channel. */
class RelaySocket private constructor() {
    val incoming = Channel<String>(Channel.UNLIMITED)
    val closed = CompletableDeferred<String>()
    private val opened = CompletableDeferred<Unit>()
    private lateinit var webSocket: WebSocket

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            opened.complete(Unit)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            incoming.trySend(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            incoming.trySend(bytes.utf8())
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            finish("relay closed ($code${if (reason.isNotBlank()) " $reason" else ""})")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            finish("socket closed ($code)")
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val detail = t.message ?: t.javaClass.simpleName
            opened.completeExceptionally(TransportException(detail, t))
            finish(detail)
        }
    }

    private fun finish(reason: String) {
        closed.complete(reason)
        incoming.close()
    }

    fun send(text: String): Boolean = webSocket.send(text)

    fun close() {
        webSocket.close(1000, null)
        finish("closed by app")
    }

    companion object {
        suspend fun open(client: OkHttpClient, url: String, timeoutMs: Long): RelaySocket {
            val socket = RelaySocket()
            socket.webSocket = client.newWebSocket(Request.Builder().url(url).build(), socket.listener)
            try {
                withTimeout(timeoutMs) { socket.opened.await() }
            } catch (e: Exception) {
                socket.webSocket.cancel()
                throw e
            }
            return socket
        }
    }
}

/** A socket after a completed Noise handshake. */
class SecureChannel(val socket: RelaySocket, private val session: NoiseSession) {
    private val lock = Any()
    val remoteStaticKey: ByteArray get() = session.remoteStaticKey

    /** Encrypt+enqueue under one lock so nonce order always matches frame order. */
    fun send(plaintext: String): Boolean = synchronized(lock) {
        val ciphertext = session.encrypt(plaintext.toByteArray(Charsets.UTF_8))
        val wire = WireMessage("transport", Base64.getEncoder().encodeToString(ciphertext))
        socket.send(wireJson.encodeToString(WireMessage.serializer(), wire))
    }

    /** Decrypt one raw frame; null for frames that are not transport payloads. */
    fun decryptFrame(raw: String): String? {
        val wire = runCatching { wireJson.decodeFromString(WireMessage.serializer(), raw) }.getOrNull() ?: return null
        if (wire.phase != "transport") return null
        val bytes = Base64.getDecoder().decode(wire.payload)
        return String(session.decrypt(bytes), Charsets.UTF_8)
    }

    companion object {
        /**
         * Run the handshake as initiator. `remoteStaticKey` null → XX (first pairing),
         * non-null → IK (reconnect to a trusted bridge).
         */
        suspend fun handshake(
            socket: RelaySocket,
            staticKey: NoiseKeyPair,
            remoteStaticKey: ByteArray?,
            timeoutMs: Long,
        ): SecureChannel {
            val pattern = if (remoteStaticKey != null) HandshakePattern.IK else HandshakePattern.XX
            val hs = NoiseHandshake(pattern, NoiseRole.Initiator, staticKey, remoteStaticKey)
            withTimeout(timeoutMs) {
                while (!hs.isComplete) {
                    if (hs.isMySend) {
                        val payload = hs.writeMessage()
                        val wire = WireMessage("handshake", Base64.getEncoder().encodeToString(payload))
                        if (!socket.send(wireJson.encodeToString(WireMessage.serializer(), wire))) {
                            throw TransportException("relay socket closed during handshake")
                        }
                    } else {
                        val raw = socket.incoming.receiveCatching().getOrNull()
                            ?: throw TransportException("relay closed during handshake")
                        val wire = wireJson.decodeFromString(WireMessage.serializer(), raw)
                        if (wire.phase != "handshake") throw TransportException("transport frame before handshake completed")
                        hs.readMessage(Base64.getDecoder().decode(wire.payload))
                    }
                }
            }
            return SecureChannel(socket, hs.finalizeSession())
        }
    }
}
