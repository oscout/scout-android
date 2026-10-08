package app.openscout.scout.core.pairing

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * QR decoding for the pairing scanner, on ZXing (Apache 2.0) so the app carries no
 * closed-source SDK. Works on a luma plane whose rows may be padded to [rowStride].
 * QR finder patterns are rotation-invariant, so sensor orientation doesn't matter.
 */
object QrLuma {
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )

    fun decode(reader: QRCodeReader, luma: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        if (width <= 0 || height <= 0 || rowStride < width || luma.size < rowStride * (height - 1) + width) return null
        // The last row may be unpadded; ZXing indexes rows by stride, so size the source to whole rows.
        val rows = if (luma.size >= rowStride * height) luma else luma.copyOf(rowStride * height)
        val source = PlanarYUVLuminanceSource(rows, rowStride, height, 0, 0, width, height, false)
        return try {
            reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
        } catch (_: ReaderException) {
            null
        } finally {
            reader.reset()
        }
    }
}
