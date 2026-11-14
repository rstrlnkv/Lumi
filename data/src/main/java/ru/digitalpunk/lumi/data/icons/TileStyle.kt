package ru.digitalpunk.lumi.data.icons

import kotlin.math.pow

enum class TileMode { Image, SmallIcon, Letter }

/**
 * How a stored icon is drawn. A sharp or good opaque icon fills the tile; a small one becomes a letter tile on a [bigTile]
 * (the start page) and stays the small icon in a row. An icon stored before Icons3 ([IconClass.None]) fills only when opaque.
 */
fun tileMode(cls: IconClass, bigTile: Boolean, fillsTile: Boolean): TileMode = when (cls) {
    IconClass.Sharp, IconClass.Good -> if (fillsTile) TileMode.Image else TileMode.SmallIcon
    IconClass.Vector -> TileMode.SmallIcon
    IconClass.Small, IconClass.None -> when {
        fillsTile && cls == IconClass.None -> TileMode.Image
        bigTile -> TileMode.Letter
        else -> TileMode.SmallIcon
    }
}

/** A letter tile's colours (ARGB): the [foreground] is white, or black on a light [background] where white has under 3:1 contrast. */
data class LetterColours(val background: Int, val foreground: Int)

private fun hsv(c: Int): Pair<Double, Double> {
    val r = c shr 16 and 0xFF; val g = c shr 8 and 0xFF; val b = c and 0xFF
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    return (if (max == 0) 0.0 else (max - min).toDouble() / max) to max / 255.0
}

/** Saturated and not dark: greys, near-white and near-black make no site colour. */
private fun colourful(c: Int): Boolean = hsv(c).let { (s, v) -> s >= 0.25 && v >= 0.2 }

/** The main colour of an icon's pixels (ARGB): the heaviest 4-bit bin of opaque, colourful pixels, weighted by saturation. Null when none. */
fun dominantColour(pixels: IntArray): Int? {
    val weight = HashMap<Int, DoubleArray>()
    for (p in pixels) {
        if (p ushr 24 < 128 || !colourful(p)) continue
        val bin = (p shr 20 and 0xF shl 8) or (p shr 12 and 0xF shl 4) or (p shr 4 and 0xF)
        val w = 1 + 3 * hsv(p).first
        val acc = weight.getOrPut(bin) { DoubleArray(5) }
        acc[0] += w; acc[1] += (p shr 16 and 0xFF); acc[2] += (p shr 8 and 0xFF); acc[3] += (p and 0xFF); acc[4] += 1.0
    }
    val top = weight.values.maxByOrNull { it[0] } ?: return null
    return (0xFF shl 24) or ((top[1] / top[4]).toInt() shl 16) or ((top[2] / top[4]).toInt() shl 8) or (top[3] / top[4]).toInt()
}

/** The tile colour: the favicon's colour, else the page's theme-color, else a stable colour made from [host]. */
fun tileColour(dominant: Int?, themeColor: Int?, host: String): Int =
    listOfNotNull(dominant, themeColor).map { it or (0xFF shl 24) }.firstOrNull(::colourful) ?: hostColour(host)

/** Hue from the host, saturation 0.45, value 0.60. */
private fun hostColour(host: String): Int {
    val h = Math.floorMod(host.hashCode(), 360) / 60.0
    val v = 0.60; val c = v * 0.45; val x = c * (1 - kotlin.math.abs(h % 2 - 1)); val m = v - c
    val (r, g, b) = when (h.toInt()) { 0 -> Triple(c, x, 0.0); 1 -> Triple(x, c, 0.0); 2 -> Triple(0.0, c, x); 3 -> Triple(0.0, x, c); 4 -> Triple(x, 0.0, c); else -> Triple(c, 0.0, x) }
    fun ch(d: Double) = ((d + m) * 255).toInt().coerceIn(0, 255)
    return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(b)
}

private fun luminance(c: Int): Double {
    fun ch(v: Int) = (v / 255.0).let { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
    return 0.2126 * ch(c shr 16 and 0xFF) + 0.7152 * ch(c shr 8 and 0xFF) + 0.0722 * ch(c and 0xFF)
}

/** White when it has at least 3:1 contrast on [background] (the letter is a 64 dp glyph: WCAG large text), else black. */
fun letterColours(background: Int): LetterColours {
    val l = luminance(background)
    return LetterColours(background, if (1.05 / (l + 0.05) >= 3.0) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
}
