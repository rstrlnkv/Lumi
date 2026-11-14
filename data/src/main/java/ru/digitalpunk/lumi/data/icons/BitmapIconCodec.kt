package ru.digitalpunk.lumi.data.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream

private const val MAX_DECODE_EDGE = 2048
private const val WORKING_EDGE = 256

/** Decodes PNG, JPEG, WebP, GIF or ICO through the platform, or a safe SVG through AndroidSVG, and stores a 192 px PNG per [planIcon]. */
class BitmapIconCodec : IconCodec {
    override fun encode(bytes: ByteArray): EncodedIcon? {
        if (looksLikeSvg(bytes)) return encodeSvg(bytes)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val w = bounds.outWidth
        val h = bounds.outHeight
        if (w <= 0 || h <= 0 || w > MAX_DECODE_EDGE || h > MAX_DECODE_EDGE) return null
        var sample = 1
        while (maxOf(w, h) / (sample * 2) >= WORKING_EDGE) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
        val src = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        try {
            val pixels = IntArray(src.width * src.height)
            src.getPixels(pixels, 0, src.width, 0, 0, src.width, src.height)
            val plan = planIcon(src.width, src.height, fillsTile(pixels)) ?: return null
            val out = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
            try {
                Canvas(out).drawBitmap(
                    src,
                    Rect(plan.srcX, plan.srcY, plan.srcX + plan.srcW, plan.srcY + plan.srcH),
                    Rect(plan.dstX, plan.dstY, plan.dstX + plan.dstW, plan.dstY + plan.dstH),
                    Paint(Paint.FILTER_BITMAP_FLAG),
                )
                val png = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                return EncodedIcon(png, IconQuality(minOf(w, h), vector = false))
            } finally {
                out.recycle()
            }
        } finally {
            src.recycle()
        }
    }

    private fun encodeSvg(bytes: ByteArray): EncodedIcon? {
        if (bytes.size > MAX_SVG_BYTES) return null
        val text = String(bytes, Charsets.UTF_8)
        if (!svgSafe(text)) return null
        val out = SvgRaster.render(text) ?: return null
        try {
            val pixels = IntArray(ICON_SIZE * ICON_SIZE)
            out.getPixels(pixels, 0, ICON_SIZE, 0, 0, ICON_SIZE, ICON_SIZE)
            if (!hasInk(pixels)) return null
            val png = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            return EncodedIcon(png, IconQuality(ICON_SIZE, vector = true))
        } finally {
            out.recycle()
        }
    }
}
