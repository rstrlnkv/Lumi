package ru.digitalpunk.lumi.gesture

/**
 * Address-bar heights in px, all measured from the screen bottom (so they include the navigation
 * inset). `expanded` is the bar fully shown, `collapsed` the strip left when it is hidden.
 */
object BarGeometry {
    fun maxHidden(expanded: Float, collapsed: Float) = (expanded - collapsed).coerceAtLeast(0f)

    /**
     * Height the bar covers right now: what Gecko gets as the vertical clipping. If the page ends at
     * the wrong place on a device, this is the one line to flip.
     */
    fun clipping(expanded: Float, hidden: Float) = expanded - hidden

    /** Screen y of the top of the bar box, which is where the collapsed strip's tap layer starts. */
    fun stripTop(screenHeight: Float, expanded: Float, hidden: Float) = screenHeight - expanded + hidden

    /** 0 = expanded, 1 = collapsed. */
    fun progress(hidden: Float, maxHidden: Float) = if (maxHidden <= 0f) 1f else (hidden / maxHidden).coerceIn(0f, 1f)
}
