// Noise Protocol Framework — wire-compatible with the bridge's TypeScript
// implementation (packages/runtime/src/pairing-security/noise.ts) and the iOS
// port (packages/scout-ios-core/Sources/ScoutIOSCore/NoiseProtocol.swift).
//
// Cipher suite: Noise_XX_25519_AESGCM_SHA256 (first pairing) and
// Noise_IK_25519_AESGCM_SHA256 (reconnect to a known bridge). The phone is
// always the initiator.

package app.openscout.scout.core.crypto

import org.bouncycastle.math.ec.rfc7748.X25519
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val DHLEN = 32
private const val HASHLEN = 32
private const val TAGLEN = 16

class NoiseKeyPair(val publicKey: ByteArray, val privateKey: ByteArray) {
    init {
        require(publicKey.size == 32 && privateKey.size == 32) { "X25519 keys are 32 bytes" }
    }

    companion object {
        private val random = SecureRandom()

        fun generate(): NoiseKeyPair {
            val priv = ByteArray(X25519.SCALAR_SIZE)
            X25519.generatePrivateKey(random, priv)
            return fromPrivateKey(priv)
        }

        fun fromPrivateKey(privateKey: ByteArray): NoiseKeyPair {
            val pub = ByteArray(X25519.POINT_SIZE)
            X25519.generatePublicKey(privateKey, 0, pub, 0)
            return NoiseKeyPair(pub, privateKey.copyOf())
        }
    }
}

enum class NoiseRole { Initiator, Responder }

enum class HandshakePattern(val wireName: String) { XX("XX"), IK("IK") }

class NoiseException(message: String, cause: Throwable? = null) : Exception(message, cause)

internal fun noiseDh(privateKey: ByteArray, publicKey: ByteArray): ByteArray {
    val out = ByteArray(32)
    val ok = X25519.calculateAgreement(privateKey, 0, publicKey, 0, out, 0)
    if (!ok) throw NoiseException("Noise: X25519 produced an all-zero shared secret")
    return out
}

private fun sha256(vararg parts: ByteArray): ByteArray {
    val md = MessageDigest.getInstance("SHA-256")
    parts.forEach(md::update)
    return md.digest()
}

private fun hmacSha256(key: ByteArray, vararg parts: ByteArray): ByteArray {
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(key, "HmacSHA256"))
    parts.forEach(mac::update)
    return mac.doFinal()
}

/** RFC 5869 HKDF-SHA256 with empty info — the same call noble/hashes makes in noise.ts. */
internal fun noiseHkdf(salt: ByteArray, ikm: ByteArray, length: Int): ByteArray {
    val prk = hmacSha256(salt, ikm)
    val out = ByteArray(length)
    var previous = ByteArray(0)
    var offset = 0
    var counter = 1
    while (offset < length) {
        previous = hmacSha256(prk, previous, byteArrayOf(counter.toByte()))
        val take = minOf(previous.size, length - offset)
        System.arraycopy(previous, 0, out, offset, take)
        offset += take
        counter++
    }
    return out
}

/** Symmetric AES-256-GCM with the Noise nonce counter. */
class CipherState(private var key: ByteArray? = null) {
    private var nonce: Long = 0

    val hasKey: Boolean get() = key != null

    private fun nonceBytes(): ByteArray {
        // 4 zero bytes + 8-byte little-endian counter.
        val buf = ByteArray(12)
        var n = nonce
        for (i in 4 until 12) {
            buf[i] = (n and 0xFF).toByte()
            n = n ushr 8
        }
        return buf
    }

    fun encryptWithAd(ad: ByteArray, plaintext: ByteArray): ByteArray {
        val k = key ?: return plaintext
        if (nonce == -1L) throw NoiseException("Noise: nonce exhausted")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(TAGLEN * 8, nonceBytes()))
        cipher.updateAAD(ad)
        val out = cipher.doFinal(plaintext) // ciphertext || tag
        nonce++
        return out
    }

    fun decryptWithAd(ad: ByteArray, ciphertext: ByteArray): ByteArray {
        val k = key ?: return ciphertext
        if (ciphertext.size < TAGLEN) throw NoiseException("Noise: ciphertext too short")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(k, "AES"), GCMParameterSpec(TAGLEN * 8, nonceBytes()))
        cipher.updateAAD(ad)
        val out = try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw NoiseException("Noise: decryption failed", e)
        }
        nonce++
        return out
    }
}

internal class SymmetricState(protocolName: String) {
    private var ck: ByteArray
    private var h: ByteArray
    var cipher = CipherState()
        private set

    init {
        val name = protocolName.toByteArray(Charsets.UTF_8)
        h = if (name.size <= HASHLEN) name.copyOf(HASHLEN) else sha256(name)
        ck = h.copyOf()
    }

    val handshakeHash: ByteArray get() = h.copyOf()

    fun mixKey(ikm: ByteArray) {
        val output = noiseHkdf(ck, ikm, 64)
        ck = output.copyOfRange(0, 32)
        cipher = CipherState(output.copyOfRange(32, 64))
    }

    fun mixHash(data: ByteArray) {
        h = sha256(h, data)
    }

    fun encryptAndHash(plaintext: ByteArray): ByteArray {
        val ciphertext = cipher.encryptWithAd(h, plaintext)
        mixHash(ciphertext)
        return ciphertext
    }

    fun decryptAndHash(ciphertext: ByteArray): ByteArray {
        val plaintext = cipher.decryptWithAd(h, ciphertext)
        mixHash(ciphertext)
        return plaintext
    }

    fun split(): Pair<CipherState, CipherState> {
        val output = noiseHkdf(ck, ByteArray(0), 64)
        return CipherState(output.copyOfRange(0, 32)) to CipherState(output.copyOfRange(32, 64))
    }
}

/** Result of a completed handshake: transport ciphers + the authenticated remote static key. */
class NoiseSession internal constructor(
    private val sendCipher: CipherState,
    private val recvCipher: CipherState,
    val remoteStaticKey: ByteArray,
    val handshakeHash: ByteArray,
) {
    fun encrypt(plaintext: ByteArray): ByteArray = sendCipher.encryptWithAd(ByteArray(0), plaintext)
    fun decrypt(ciphertext: ByteArray): ByteArray = recvCipher.decryptWithAd(ByteArray(0), ciphertext)
}

private class PatternDef(val pre: List<List<String>>, val messages: List<List<String>>)

private val PATTERNS = mapOf(
    HandshakePattern.XX to PatternDef(
        pre = emptyList(),
        messages = listOf(listOf("e"), listOf("e", "ee", "s", "es"), listOf("s", "se")),
    ),
    HandshakePattern.IK to PatternDef(
        pre = listOf(emptyList(), listOf("s")),
        messages = listOf(listOf("e", "es", "s", "ss"), listOf("e", "ee", "se")),
    ),
)

class NoiseHandshake(
    pattern: HandshakePattern,
    private val role: NoiseRole,
    private val s: NoiseKeyPair,
    remoteStaticKey: ByteArray? = null,
    /** Test hook: deterministic ephemeral keys. */
    private val ephemeralFactory: () -> NoiseKeyPair = NoiseKeyPair::generate,
) {
    private val ss = SymmetricState("Noise_${pattern.wireName}_25519_AESGCM_SHA256")
    private val messages = PATTERNS.getValue(pattern).messages
    private var e: NoiseKeyPair? = null
    private var rs: ByteArray? = null
    private var re: ByteArray? = null
    private var messageIndex = 0

    init {
        val pre = PATTERNS.getValue(pattern).pre
        pre.getOrNull(0)?.forEach { token ->
            if (token == "s") {
                val key = if (role == NoiseRole.Initiator) s.publicKey
                else requireNotNull(remoteStaticKey) { "Noise: missing initiator static key" }.also { rs = it }
                ss.mixHash(key)
            }
        }
        pre.getOrNull(1)?.forEach { token ->
            if (token == "s") {
                val key = if (role == NoiseRole.Responder) s.publicKey
                else requireNotNull(remoteStaticKey) { "Noise: IK requires the responder static key" }.also { rs = it }
                ss.mixHash(key)
            }
        }
        if (remoteStaticKey != null && rs == null) rs = remoteStaticKey
    }

    val isMySend: Boolean get() = (role == NoiseRole.Initiator) == (messageIndex % 2 == 0)
    val isComplete: Boolean get() = messageIndex >= messages.size

    fun writeMessage(payload: ByteArray = ByteArray(0)): ByteArray {
        if (!isMySend) throw NoiseException("Noise: not our turn to send")
        if (isComplete) throw NoiseException("Noise: handshake already complete")
        val out = java.io.ByteArrayOutputStream()
        for (token in messages[messageIndex]) {
            when (token) {
                "e" -> {
                    val eph = ephemeralFactory()
                    e = eph
                    out.write(eph.publicKey)
                    ss.mixHash(eph.publicKey)
                }
                "s" -> out.write(ss.encryptAndHash(s.publicKey))
                else -> performDh(token)
            }
        }
        out.write(ss.encryptAndHash(payload))
        messageIndex++
        return out.toByteArray()
    }

    fun readMessage(message: ByteArray): ByteArray {
        if (isMySend) throw NoiseException("Noise: not our turn to receive")
        if (isComplete) throw NoiseException("Noise: handshake already complete")
        var offset = 0
        for (token in messages[messageIndex]) {
            when (token) {
                "e" -> {
                    if (offset + DHLEN > message.size) throw NoiseException("Noise: message too short")
                    val remote = message.copyOfRange(offset, offset + DHLEN)
                    re = remote
                    offset += DHLEN
                    ss.mixHash(remote)
                }
                "s" -> {
                    val len = if (ss.cipher.hasKey) DHLEN + TAGLEN else DHLEN
                    if (offset + len > message.size) throw NoiseException("Noise: message too short")
                    rs = ss.decryptAndHash(message.copyOfRange(offset, offset + len))
                    offset += len
                }
                else -> performDh(token)
            }
        }
        val payload = ss.decryptAndHash(message.copyOfRange(offset, message.size))
        messageIndex++
        return payload
    }

    fun finalizeSession(): NoiseSession {
        if (!isComplete) throw NoiseException("Noise: handshake not complete")
        val remote = rs ?: throw NoiseException("Noise: remote static key not established")
        val (c1, c2) = ss.split()
        val (send, recv) = if (role == NoiseRole.Initiator) c1 to c2 else c2 to c1
        return NoiseSession(send, recv, remote.copyOf(), ss.handshakeHash)
    }

    private fun performDh(token: String) {
        if (token.length != 2) throw NoiseException("Noise: invalid DH token '$token'")
        val initiator = role == NoiseRole.Initiator
        val mine = if (initiator) token[0] else token[1]
        val theirs = if (initiator) token[1] else token[0]
        val myPrivate = when (mine) {
            'e' -> e?.privateKey ?: throw NoiseException("Noise: missing local ephemeral for DH($token)")
            's' -> s.privateKey
            else -> throw NoiseException("Noise: invalid DH token '$token'")
        }
        val theirPublic = when (theirs) {
            'e' -> re ?: throw NoiseException("Noise: missing remote ephemeral for DH($token)")
            's' -> rs ?: throw NoiseException("Noise: missing remote static for DH($token)")
            else -> throw NoiseException("Noise: invalid DH token '$token'")
        }
        ss.mixKey(noiseDh(myPrivate, theirPublic))
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xFF) }

fun String.hexToBytes(): ByteArray {
    val clean = trim()
    require(clean.length % 2 == 0) { "hex string must have an even length" }
    return ByteArray(clean.length / 2) { i -> clean.substring(i * 2, i * 2 + 2).toInt(16).toByte() }
}
