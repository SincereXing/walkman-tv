@file:Suppress("DEPRECATION")

package com.walkman.tv.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import coil.size.Size
import coil.transform.Transformation
import kotlin.math.max

/**
 * Coil [Transformation] that pre-scales the input bitmap and blurs the result via
 * [ScriptIntrinsicBlur]. Equivalent to what `io.coil-kt:coil-transformations:BlurTransformation`
 * used to provide before that artifact stopped tracking the coil-compose 2.x releases.
 *
 * Runs on RenderScript so it works back to API 21 — covers our minSdk + the 32-bit ARM
 * Android-10 TVs where [androidx.compose.ui.draw.blur] (API 31+) isn't available.
 *
 * RenderScript was deprecated in API 31 but still ships (and works) on later platforms; we
 * suppress the deprecation warnings file-wide since there is no equivalent path that covers
 * API 21–30 + 31+ uniformly.
 *
 * @param radius   Gaussian radius (0 < r ≤ 25 — RenderScript's hard cap).
 * @param sampling Pre-downscale factor (≥1). Bigger value = faster + softer.
 */
class BlurTransformation(
    private val context: Context,
    private val radius: Float = 20f,
    private val sampling: Float = 2f,
) : Transformation {

    override val cacheKey: String = "${javaClass.name}-radius=$radius-sampling=$sampling"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val srcW = input.width.toFloat()
        val srcH = input.height.toFloat()
        val s = sampling.coerceAtLeast(1f)
        val dstW = max(1, (srcW / s).toInt())
        val dstH = max(1, (srcH / s).toInt())

        // 1) Downscale into ARGB_8888 — RenderScript blur needs uchar4 input.
        val scaled = Bitmap.createBitmap(dstW, dstH, Bitmap.Config.ARGB_8888)
        Canvas(scaled).apply {
            scale(1f / s, 1f / s)
            drawBitmap(input, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG))
        }

        // 2) Apply Gaussian via RenderScript.
        var rs: RenderScript? = null
        var inAlloc: Allocation? = null
        var outAlloc: Allocation? = null
        var script: ScriptIntrinsicBlur? = null
        try {
            rs = RenderScript.create(context)
            inAlloc = Allocation.createFromBitmap(rs, scaled)
            outAlloc = Allocation.createTyped(rs, inAlloc.type)
            script = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            script.setRadius(radius.coerceIn(0.1f, 25f))
            script.setInput(inAlloc)
            script.forEach(outAlloc)
            outAlloc.copyTo(scaled)
        } finally {
            script?.destroy()
            outAlloc?.destroy()
            inAlloc?.destroy()
            rs?.destroy()
        }
        return scaled
    }
}
