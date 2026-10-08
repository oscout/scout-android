// Persistent phone identity + trusted bridges — the Android counterpart of
// ScoutIdentity (Identity.swift) and BridgeConnectionInfo persistence
// (BridgeConnection.swift).
//
// The X25519 static private key never leaves the app sandbox in the clear: it
// is sealed with an AES-256-GCM key that lives in the Android Keystore. Trusted
// bridge records and relay routes are not secret (the bridge key is public) and
// are stored as JSON in private SharedPreferences.

package app.openscout.scout.core.identity

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import app.openscout.scout.core.crypto.NoiseKeyPair
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

@Serializable
data class TrustedBridge(
    val publicKeyHex: String,
    val name: String? = null,
    val pairedAt: Long,
    val lastSeen: Long? = null,
)

/** Relay routes for one paired bridge. `roomId` rotates when the bridge restarts. */
@Serializable
data class BridgeConnectionInfo(
    val publicKeyHex: String,
    val relayUrl: String,
    val roomId: String,
    val fallbackRelayUrls: List<String> = emptyList(),
    val webPort: Int? = null,
) {
    val orderedRelayUrls: List<String>
        get() = (listOf(relayUrl) + fallbackRelayUrls).map { it.trim() }.filter { it.isNotEmpty() }.distinct()
}

class IdentityStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("scout.identity", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    @Volatile private var cachedKeyPair: NoiseKeyPair? = null

    @Synchronized
    fun loadOrCreateIdentity(): NoiseKeyPair {
        cachedKeyPair?.let { return it }
        val sealed = prefs.getString(KEY_STATIC_PRIVATE, null)
        val pair = if (sealed != null) {
            runCatching { NoiseKeyPair.fromPrivateKey(unseal(sealed)) }.getOrNull()
        } else null
        val result = pair ?: NoiseKeyPair.generate().also {
            prefs.edit().putString(KEY_STATIC_PRIVATE, seal(it.privateKey)).apply()
        }
        cachedKeyPair = result
        return result
    }

    // -- Trusted bridges ---------------------------------------------------

    fun trustedBridges(): List<TrustedBridge> =
        prefs.getString(KEY_TRUSTED, null)?.let {
            runCatching { json.decodeFromString(ListSerializer(TrustedBridge.serializer()), it) }.getOrNull()
        } ?: emptyList()

    fun isTrusted(publicKeyHex: String): Boolean =
        trustedBridges().any { it.publicKeyHex.equals(publicKeyHex, ignoreCase = true) }

    @Synchronized
    fun saveTrustedBridge(publicKeyHex: String, name: String?) {
        val now = System.currentTimeMillis()
        val list = trustedBridges().toMutableList()
        val idx = list.indexOfFirst { it.publicKeyHex.equals(publicKeyHex, ignoreCase = true) }
        if (idx >= 0) {
            val existing = list[idx]
            list[idx] = existing.copy(lastSeen = now, name = name ?: existing.name)
        } else {
            list += TrustedBridge(publicKeyHex.lowercase(), name, pairedAt = now, lastSeen = now)
        }
        writeTrusted(list)
    }

    @Synchronized
    fun touchTrustedBridge(publicKeyHex: String) {
        val list = trustedBridges().map {
            if (it.publicKeyHex.equals(publicKeyHex, ignoreCase = true)) it.copy(lastSeen = System.currentTimeMillis()) else it
        }
        writeTrusted(list)
    }

    @Synchronized
    fun renameTrustedBridge(publicKeyHex: String, name: String) {
        writeTrusted(trustedBridges().map {
            if (it.publicKeyHex.equals(publicKeyHex, ignoreCase = true)) it.copy(name = name) else it
        })
    }

    @Synchronized
    fun forgetBridge(publicKeyHex: String) {
        writeTrusted(trustedBridges().filterNot { it.publicKeyHex.equals(publicKeyHex, ignoreCase = true) })
        writeConnections(connectionInfos().filterNot { it.publicKeyHex.equals(publicKeyHex, ignoreCase = true) })
        if (activePublicKeyHex().equals(publicKeyHex, ignoreCase = true)) {
            setActivePublicKeyHex(connectionInfos().firstOrNull()?.publicKeyHex)
        }
    }

    private fun writeTrusted(list: List<TrustedBridge>) {
        prefs.edit().putString(KEY_TRUSTED, json.encodeToString(ListSerializer(TrustedBridge.serializer()), list)).apply()
    }

    // -- Connection info (relay routes) -------------------------------------

    fun connectionInfos(): List<BridgeConnectionInfo> =
        prefs.getString(KEY_CONNECTIONS, null)?.let {
            runCatching { json.decodeFromString(ListSerializer(BridgeConnectionInfo.serializer()), it) }.getOrNull()
        } ?: emptyList()

    fun activePublicKeyHex(): String? = prefs.getString(KEY_ACTIVE, null)

    fun setActivePublicKeyHex(hex: String?) {
        prefs.edit().apply { if (hex == null) remove(KEY_ACTIVE) else putString(KEY_ACTIVE, hex.lowercase()) }.apply()
    }

    fun activeConnectionInfo(): BridgeConnectionInfo? {
        val infos = connectionInfos()
        val active = activePublicKeyHex()
        return infos.firstOrNull { it.publicKeyHex.equals(active, ignoreCase = true) } ?: infos.firstOrNull()
    }

    @Synchronized
    fun saveConnectionInfo(info: BridgeConnectionInfo, promoteActive: Boolean = true) {
        val list = connectionInfos().filterNot { it.publicKeyHex.equals(info.publicKeyHex, ignoreCase = true) } + info
        writeConnections(list)
        if (promoteActive) setActivePublicKeyHex(info.publicKeyHex)
    }

    private fun writeConnections(list: List<BridgeConnectionInfo>) {
        prefs.edit().putString(KEY_CONNECTIONS, json.encodeToString(ListSerializer(BridgeConnectionInfo.serializer()), list)).apply()
    }

    // -- Keystore sealing ----------------------------------------------------

    private fun wrapKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEYSTORE_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun seal(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey())
        val out = cipher.iv + cipher.doFinal(plain)
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    private fun unseal(sealed: String): ByteArray {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, 12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, wrapKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(bytes, 12, bytes.size - 12)
    }

    private companion object {
        const val KEYSTORE_ALIAS = "app.openscout.scout.identity.wrap"
        const val KEY_STATIC_PRIVATE = "static-key-private.sealed"
        const val KEY_TRUSTED = "trusted-bridges"
        const val KEY_CONNECTIONS = "bridge-connections"
        const val KEY_ACTIVE = "active-bridge"
    }
}
