// Pairing payload — the same bytes the iOS app scans (QRPayload.swift) and the
// bridge renders (packages/runtime/src/pairing-security/identity.ts):
//   { v: 1, relay: "ws://...", fallbackRelays?: [...], room: "uuid",
//     publicKey: "hex64", expiresAt: unixMs, webPort?: n }
// No shared secret travels in it; trust comes from the Noise XX handshake
// matching publicKey.

package app.openscout.scout.core.pairing

import app.openscout.scout.core.crypto.hexToBytes
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLDecoder

@Serializable
data class QRPayload(
    val v: Int = 1,
    val relay: String,
    val fallbackRelays: List<String>? = null,
    val room: String,
    val publicKey: String,
    val expiresAt: Long = 0,
    val webPort: Int? = null,
) {
    /** Null when valid, otherwise an operator-facing reason. */
    fun validate(nowMs: Long = System.currentTimeMillis()): String? {
        if (v != 1) return "Unsupported pairing version $v. Update Scout for Android."
        if (publicKey.length != 64 || !publicKey.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
            return "The pairing code has an invalid bridge key."
        }
        if (relay.isBlank()) return "The pairing code is missing its relay address."
        if (room.isBlank()) return "The pairing code is missing its room."
        if (expiresAt in 1 until nowMs) return "This pairing code has expired. Show a fresh one on your computer."
        if (webPort != null && webPort !in 1..65535) return "The pairing code has an invalid web port."
        return null
    }

    val bridgePublicKey: ByteArray get() = publicKey.hexToBytes()

    /** Relay URLs in attempt order, de-duplicated. */
    val orderedRelayUrls: List<String>
        get() = (listOf(relay) + (fallbackRelays ?: emptyList()))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

    companion object {
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }

        fun parseJson(raw: String): QRPayload = json.decodeFromString(serializer(), raw.trim())

        /**
         * Accepts the raw JSON, the `https://openscout.app/pair#payload=…` link
         * the QR encodes, or a `scout://pair?…` deep link carrying either a
         * single `payload` item or the individual fields.
         */
        fun parse(input: String): QRPayload {
            val trimmed = input.trim()
            if (trimmed.startsWith("{")) return parseJson(trimmed)
            val uri = try {
                URI(trimmed)
            } catch (e: Exception) {
                throw IllegalArgumentException("Not a pairing link or pairing JSON")
            }
            val items = mutableListOf<Pair<String, String>>()
            uri.rawQuery?.let { items += parseQuery(it) }
            uri.rawFragment?.let { items += parseQuery(it) }
            fun value(key: String) = items.firstOrNull { it.first == key }?.second
            value("payload")?.let { return parseJson(it) }
            val relay = value("relay")
            val room = value("room")
            val publicKey = value("publicKey")
            if (relay == null || room == null || publicKey == null) {
                throw IllegalArgumentException("The pairing link is missing relay, room, or publicKey")
            }
            val fallbacks = items.filter { it.first == "fallbackRelay" }.map { it.second }
            return QRPayload(
                v = value("v")?.toIntOrNull() ?: 1,
                relay = relay,
                fallbackRelays = fallbacks.ifEmpty { null },
                room = room,
                publicKey = publicKey,
                expiresAt = value("expiresAt")?.toLongOrNull() ?: 0,
                webPort = value("webPort")?.toIntOrNull(),
            )
        }

        private fun parseQuery(raw: String): List<Pair<String, String>> =
            raw.split('&').filter { it.isNotEmpty() }.map { part ->
                val idx = part.indexOf('=')
                if (idx < 0) decode(part) to "" else decode(part.substring(0, idx)) to decode(part.substring(idx + 1))
            }

        private fun decode(s: String): String = URLDecoder.decode(s.replace("+", "%2B"), "UTF-8")
    }
}
