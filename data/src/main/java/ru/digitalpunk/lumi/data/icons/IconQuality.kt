package ru.digitalpunk.lumi.data.icons

/** Safari's apple-touch size: an icon this large is sharp in the 192 px store. */
internal const val SHARP_PX = 180
/** Under this the 64 dp start-page tile would upscale the icon at least 1.33 times: it is drawn as a letter tile instead. */
internal const val GOOD_PX = 96

/** What a decoded icon was made from: [srcPx] is the shorter edge of the source; a rasterised SVG is [vector]. */
data class IconQuality(val srcPx: Int, val vector: Boolean)

/** Better classes come later: a 120 px raster beats an SVG, an SVG beats a favicon. [None] is no icon or one stored before Icons3. */
enum class IconClass { None, Small, Vector, Good, Sharp }

fun iconClass(q: IconQuality?): IconClass = when {
    q == null || (!q.vector && q.srcPx <= 0) -> IconClass.None
    q.vector -> IconClass.Vector
    q.srcPx >= SHARP_PX -> IconClass.Sharp
    q.srcPx >= GOOD_PX -> IconClass.Good
    else -> IconClass.Small
}

/** A new icon replaces the stored one only when it is not worse: a higher class, or the same class and at least as large. */
fun shouldReplace(stored: IconQuality?, new: IconQuality): Boolean {
    val old = iconClass(stored)
    val now = iconClass(new)
    return now > old || (now == old && new.srcPx >= (stored?.srcPx ?: 0))
}
