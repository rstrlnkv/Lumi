package ru.digitalpunk.lumi.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** WCAG 2 relative luminance of an sRGB colour; alpha is ignored (site colours are opaque). */
fun relativeLuminance(argb: Int): Double {
    fun linear(channel: Int): Double {
        val c = channel / 255.0
        return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * linear(argb shr 16 and 0xFF) + 0.7152 * linear(argb shr 8 and 0xFF) + 0.0722 * linear(argb and 0xFF)
}

/** WCAG 2 contrast ratio of two relative luminances, 1 to 21. */
fun contrast(l1: Double, l2: Double): Double = (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)

/** Black system-bar icons read better on [argb] than white ones (black wins a tie): the luminance is above about 0.179. */
fun darkIconsOn(argb: Int): Boolean {
    val l = relativeLuminance(argb)
    return contrast(l, 0.0) >= contrast(1.0, l)
}

/** The band under the Compact bar: the site bar's colour when the page reported one, else [surface]. */
fun bandColor(site: Int?, surface: Color): Color = site?.let(::Color) ?: surface
