package org.perceivers25.warpinator.core.utils

import android.graphics.Bitmap
import androidx.core.graphics.createBitmap
import androidx.core.graphics.set
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class QRCodeBitmaps {
    companion object {
        /**
         * Generates a QR code bitmap from the provided [text].
         *
         * This function uses the ZXING library to encode the string into a 128x128
         * [Bitmap.Config.ALPHA_8] bitmap. The operation is performed on the
         * [Dispatchers.Default] coroutine dispatcher.
         *
         * @param text The string content to be encoded into the QR code.
         * @return A [Bitmap] representing the QR code, or `null` if a [WriterException] occurs.
         */
        suspend fun generate(text: String): Bitmap? =
            withContext(Dispatchers.Default) {
                val writer = QRCodeWriter()
                try {
                    val hints = mapOf(
                        EncodeHintType.MARGIN to 0,
                    )
                    val bitMatrix = writer.encode(
                        text,
                        BarcodeFormat.QR_CODE,
                        128,
                        128,
                        hints,
                    )
                    val width = bitMatrix.width
                    val height = bitMatrix.height
                    val bmp = createBitmap(width, height, Bitmap.Config.ALPHA_8)
                    for (x in 0..<width) {
                        for (y in 0..<height) {
                            bmp[x, y] = if (bitMatrix.get(
                                    x, y,
                                )
                            ) android.graphics.Color.BLACK else android.graphics.Color.TRANSPARENT
                        }
                    }
                    return@withContext bmp
                } catch (e: WriterException) {
                    e.printStackTrace()
                }
                return@withContext null
            }
    }
}