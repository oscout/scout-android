package app.openscout.scout

import app.openscout.scout.core.pairing.QRPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.net.URLEncoder

class QRPayloadTest {
    private val key = "0f1e2d3c4b5a69788796a5b4c3d2e1f00f1e2d3c4b5a69788796a5b4c3d2e1f0"
    private val json =
        """{"v":1,"relay":"ws://192.0.2.10:43131","fallbackRelays":["ws://host.ts.net:43131"],"room":"00000000-0000-4000-8000-000000000001","publicKey":"$key","expiresAt":${Long.MAX_VALUE / 2}}"""

    @Test fun parsesRawJson() {
        val p = QRPayload.parse(json)
        assertEquals(listOf("ws://192.0.2.10:43131", "ws://host.ts.net:43131"), p.orderedRelayUrls)
        assertNull(p.validate())
    }

    @Test fun parsesScoutDeepLinkWithPayload() {
        val p = QRPayload.parse("scout://pair?payload=" + URLEncoder.encode(json, "UTF-8"))
        assertEquals(key, p.publicKey)
    }

    @Test fun parsesOpenScoutLinkFragment() {
        val p = QRPayload.parse("https://openscout.app/pair#payload=" + URLEncoder.encode(json, "UTF-8"))
        assertEquals("00000000-0000-4000-8000-000000000001", p.room)
    }

    @Test fun parsesIndividualFields() {
        val p = QRPayload.parse("scout://pair?relay=ws%3A%2F%2F10.0.0.2%3A43131&room=r1&publicKey=$key&fallbackRelay=ws%3A%2F%2Fa%3A1&fallbackRelay=ws%3A%2F%2Fb%3A2")
        assertEquals(listOf("ws://10.0.0.2:43131", "ws://a:1", "ws://b:2"), p.orderedRelayUrls)
    }

    @Test fun rejectsExpiredAndBadKeys() {
        assertNotNull(QRPayload.parse(json.replace(key, "zz")).validate())
        assertNotNull(QRPayload.parse(json.replace("${Long.MAX_VALUE / 2}", "1000")).validate())
    }
}
