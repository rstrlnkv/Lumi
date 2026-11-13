package ru.digitalpunk.lumi.data.icons

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import java.io.ByteArrayOutputStream

private const val MAX_DECODE_EDGE = 2048
private const val WORKING_EDGE = 256

/** Decodes PNG, JPEG, WebP, GIF or ICO through the platform and stores the result as a 96 px PNG per [planIcon]. */
class BitmapIconCodec : IconCodec {
    override fun encode(bytes: ByteArray): ByteArray? {
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
                return ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            } finally {
                out.recycle()
            }
        } finally {
            src.recycle()
        }
    }
}
