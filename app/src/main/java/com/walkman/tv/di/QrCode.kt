package com.walkman.tv.di

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Encode [text] into a black-on-white QR bitmap (square, [size] px). Returns null on failure. */
fun encodeQrToBitmap(text: String, size: Int = 512): Bitmap? = runCatching {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 1)
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    // 先填 IntArray 再一次 setPixels：逐个 setPixel 是 26 万次 JNI 调用，电视 CPU 上首次要卡一两秒。
    val pixels = IntArray(size * size) { i -> if (matrix[i % size, i / size]) Color.BLACK else Color.WHITE }
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply { setPixels(pixels, 0, size, 0, 0, size, size) }
}.getOrNull()
