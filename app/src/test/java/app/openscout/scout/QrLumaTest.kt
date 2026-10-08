package app.openscout.scout

import app.openscout.scout.core.pairing.QrLuma
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeReader
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QrLumaTest {
    private val payload = """{"v":1,"relay":"ws://192.0.2.10:43131","room":"00000000-0000-4000-8000-000000000000","publicKey":"${"ab".repeat(32)}","expiresAt":1791474183591}"""

    /** Renders [text] as a QR into a luma plane: [margin] px of grey around it, rows padded to [stride]. */
    private fun frame(text: String, size: Int, stride: Int, margin: Int, rotate: Boolean = false): Triple<ByteArray, Int, Int> {
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val w = size + 2 * margin
        val luma = ByteArray(stride * w) { 0x80.toByte() }
        for (y in 0 until w) for (x in 0 until w) {
            val mx = x - margin
            val my = y - margin
            val inside = mx in 0 until size && my in 0 until size
            val dark = inside && (if (rotate) matrix.get(my, size - 1 - mx) else matrix.get(mx, my))
            luma[y * stride + x] = if (!inside) 0xC8.toByte() else if (dark) 0x10 else 0xF0.toByte()
        }
        return Triple(luma, w, w)
    }

    @Test fun decodesPairingPayloadFromPaddedRows() {
        val (luma, w, h) = frame(payload, size = 400, stride = 512, margin = 40)
        assertEquals(payload, QrLuma.decode(QRCodeReader(), luma, 512, w, h))
    }

    @Test fun decodesSidewaysFrame() {
        val (luma, w, h) = frame(payload, size = 400, stride = 480, margin = 20, rotate = true)
        assertEquals(payload, QrLuma.decode(QRCodeReader(), luma, 480, w, h))
    }

    @Test fun decodesWhenLastRowIsUnpadded() {
        val (luma, w, h) = frame(payload, size = 400, stride = 512, margin = 40)
        val trimmed = luma.copyOf(512 * (h - 1) + w)
        assertEquals(payload, QrLuma.decode(QRCodeReader(), trimmed, 512, w, h))
    }

    @Test fun returnsNullWithoutACode() {
        val luma = ByteArray(320 * 240) { (it % 251).toByte() }
        assertNull(QrLuma.decode(QRCodeReader(), luma, 320, 320, 240))
    }
}
