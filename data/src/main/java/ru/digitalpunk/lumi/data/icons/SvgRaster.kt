package ru.digitalpunk.lumi.data.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.RectF
import com.caverock.androidsvg.SVG
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

private const val RENDER_TIMEOUT_MS = 2_000L

/**
 * Draws a [svgSafe] SVG with a viewBox into a [ICON_SIZE] square on one daemon thread, waiting at most 2 s: a timeout
 * cannot stop the drawing, so no other SVG is drawn until that one ends and threads never pile up. Never on the main thread.
 */
internal object SvgRaster {
    private val thread = Executors.newSingleThreadExecutor { Thread(it, "svg-icon").apply { isDaemon = true } }
    private val busy = AtomicBoolean(false)

    @Synchronized
    fun render(text: String): Bitmap? {
        if (!busy.compareAndSet(false, true)) return null
        val task = thread.submit<Bitmap?> {
            try { draw(text) } finally { busy.set(false) }
        }
        return try {
            task.get(RENDER_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        } catch (_: Exception) {
            task.cancel(true)
            null
        }
    }

    private fun draw(text: String): Bitmap? {
        SVG.setInternalEntitiesEnabled(false)
        val svg = SVG.getFromString(text)
        if (svg.documentViewBox == null) return null
        val out = Bitmap.createBitmap(ICON_SIZE, ICON_SIZE, Bitmap.Config.ARGB_8888)
        svg.renderToCanvas(Canvas(out), RectF(0f, 0f, ICON_SIZE.toFloat(), ICON_SIZE.toFloat()))
        return out
    }
}
