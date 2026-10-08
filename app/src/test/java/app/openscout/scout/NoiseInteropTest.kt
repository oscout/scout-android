package app.openscout.scout

import app.openscout.scout.core.crypto.HandshakePattern
import app.openscout.scout.core.crypto.NoiseHandshake
import app.openscout.scout.core.crypto.NoiseKeyPair
import app.openscout.scout.core.crypto.NoiseRole
import app.openscout.scout.core.crypto.hexToBytes
import app.openscout.scout.core.crypto.toHex
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * The phone's initiator against the bridge's own responder
 * (packages/runtime/src/pairing-security/noise.ts) through tools/noise-responder.ts.
 * Catches what Kotlin-to-Kotlin tests can't: a drift in nonce layout, HKDF, or
 * pattern tokens on either side. Skipped when bun or the repo's node_modules are absent.
 */
class NoiseInteropTest {
    @Test
    fun interoperatesWithTheBridgeResponder() {
        val bun = (listOf(System.getenv("HOME") + "/.bun/bin/bun") + System.getenv("PATH").orEmpty().split(File.pathSeparator).map { "$it/bun" })
            .firstOrNull { File(it).canExecute() }
        val script = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "tools/noise-responder.ts") }.firstOrNull { it.exists() }
        val deps = script?.let { File(it.parentFile, "../../../packages/runtime/node_modules/@noble/ciphers").canonicalFile.exists() } == true
        assumeTrue("bun, tools/noise-responder.ts and a `bun install` are required", bun != null && deps)

        for (pattern in HandshakePattern.entries) {
            val proc = ProcessBuilder(bun!!, "run", script!!.path).start()
            val out = proc.inputStream.bufferedReader()
            val inp = proc.outputStream.bufferedWriter()
            fun send(line: String) { inp.write(line); inp.newLine(); inp.flush() }
            fun expect(prefix: String): String {
                val line = out.readLine() ?: error("responder exited: " + proc.errorStream.bufferedReader().readText())
                check(line.startsWith("$prefix ")) { "expected $prefix, got $line" }
                return line.substringAfter(' ')
            }
            try {
                val bridgeKey = expect("pub").hexToBytes()
                val phone = NoiseKeyPair.generate()
                val hs = NoiseHandshake(pattern, NoiseRole.Initiator, phone, if (pattern == HandshakePattern.IK) bridgeKey else null)
                send("pattern ${pattern.wireName}")
                var done: String? = null
                while (!hs.isComplete) {
                    if (hs.isMySend) {
                        send("msg " + hs.writeMessage().toHex())
                        if (hs.isComplete) done = expect("done")
                    } else {
                        hs.readMessage(expect("msg").hexToBytes())
                    }
                }
                val session = hs.finalizeSession()
                assertArrayEquals(bridgeKey, session.remoteStaticKey)
                assertEquals(phone.publicKey.toHex(), done ?: expect("done"))
                repeat(3) { n ->
                    send("ct " + session.encrypt("""{"id":$n,"jsonrpc":"2.0"}""".toByteArray()).toHex())
                    assertEquals("""{"ID":$n,"JSONRPC":"2.0"}""", String(session.decrypt(expect("ct").hexToBytes())))
                }
            } finally {
                proc.destroy()
            }
        }
    }
}
