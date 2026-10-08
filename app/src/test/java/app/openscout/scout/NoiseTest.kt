package app.openscout.scout

import app.openscout.scout.core.crypto.HandshakePattern
import app.openscout.scout.core.crypto.NoiseHandshake
import app.openscout.scout.core.crypto.NoiseKeyPair
import app.openscout.scout.core.crypto.NoiseRole
import app.openscout.scout.core.crypto.hexToBytes
import app.openscout.scout.core.crypto.toHex
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class NoiseTest {
    private fun run(pattern: HandshakePattern) {
        val phone = NoiseKeyPair.generate()
        val bridge = NoiseKeyPair.generate()
        val initiator = NoiseHandshake(pattern, NoiseRole.Initiator, phone, if (pattern == HandshakePattern.IK) bridge.publicKey else null)
        val responder = NoiseHandshake(pattern, NoiseRole.Responder, bridge, null)
        while (!initiator.isComplete || !responder.isComplete) {
            if (initiator.isMySend && !initiator.isComplete) responder.readMessage(initiator.writeMessage())
            else responder.writeMessage().also { initiator.readMessage(it) }
        }
        val a = initiator.finalizeSession()
        val b = responder.finalizeSession()
        assertArrayEquals(bridge.publicKey, a.remoteStaticKey)
        assertArrayEquals(phone.publicKey, b.remoteStaticKey)
        assertArrayEquals(a.handshakeHash, b.handshakeHash)
        repeat(3) { i ->
            val msg = """{"id":$i,"jsonrpc":"2.0"}""".toByteArray()
            assertArrayEquals(msg, b.decrypt(a.encrypt(msg)))
            assertArrayEquals(msg, a.decrypt(b.encrypt(msg)))
        }
    }

    @Test fun xxHandshakeRoundTrips() = run(HandshakePattern.XX)

    @Test fun ikHandshakeRoundTrips() = run(HandshakePattern.IK)

    @Test fun ikWithWrongResponderKeyFails() {
        val phone = NoiseKeyPair.generate()
        val bridge = NoiseKeyPair.generate()
        val impostor = NoiseKeyPair.generate()
        val initiator = NoiseHandshake(HandshakePattern.IK, NoiseRole.Initiator, phone, bridge.publicKey)
        val responder = NoiseHandshake(HandshakePattern.IK, NoiseRole.Responder, impostor, null)
        val first = initiator.writeMessage()
        assertThrows(Exception::class.java) { responder.readMessage(first) }
    }

    @Test fun tamperedTransportFrameIsRejected() {
        val phone = NoiseKeyPair.generate()
        val bridge = NoiseKeyPair.generate()
        val i = NoiseHandshake(HandshakePattern.XX, NoiseRole.Initiator, phone)
        val r = NoiseHandshake(HandshakePattern.XX, NoiseRole.Responder, bridge)
        r.readMessage(i.writeMessage()); i.readMessage(r.writeMessage()); r.readMessage(i.writeMessage())
        val a = i.finalizeSession()
        val b = r.finalizeSession()
        val ct = a.encrypt("hello".toByteArray())
        ct[0] = (ct[0].toInt() xor 1).toByte()
        assertThrows(Exception::class.java) { b.decrypt(ct) }
    }

    /** RFC 7748 §6.1 X25519 test vector — guards the BouncyCastle key derivation. */
    @Test fun x25519MatchesRfc7748() {
        val alicePriv = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a".hexToBytes()
        val alicePub = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a"
        assertEquals(alicePub, NoiseKeyPair.fromPrivateKey(alicePriv).publicKey.toHex())
    }
}
