package ru.digitalpunk.lumi.engine

import kotlin.math.roundToInt

internal data class CropRect(val left: Int, val top: Int, val width: Int, val height: Int)

internal data class PixelSize(val width: Int, val height: Int)

/** The top of the page at the card's [aspect] (width / height): full width, and no more height than that. Never larger than the source; empty for an empty source. */
internal fun cropRect(srcWidth: Int, srcHeight: Int, aspect: Float): CropRect {
    if (srcWidth <= 0 || srcHeight <= 0) return CropRect(0, 0, 0, 0)
    if (!(aspect > 0f)) return CropRect(0, 0, srcWidth, srcHeight)
    return CropRect(0, 0, srcWidth, (srcWidth / aspect).roundToInt().coerceIn(1, srcHeight))
}

/** [width] x [height] shrunk to [targetWidth] (at least 1), same ratio, no side below 1; a smaller image keeps its size. */
internal fun scaledSize(width: Int, height: Int, targetWidth: Int): PixelSize {
    val target = targetWidth.coerceAtLeast(1)
    return if (width <= target) PixelSize(width, height) else PixelSize(target, (height * target.toFloat() / width).roundToInt().coerceAtLeast(1))
}
