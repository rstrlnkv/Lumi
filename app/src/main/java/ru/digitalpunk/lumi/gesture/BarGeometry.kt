package ru.digitalpunk.lumi.gesture

/**
 * Address-bar heights in px, all measured from the screen bottom (so they include the navigation
 * inset). `expanded` is the bar fully shown, `collapsed` the strip left when it is hidden.
 */
object BarGeometry {
    fun maxHidden(expanded: Float, collapsed: Float) = (expanded - collapsed).coerceAtLeast(0f)

    /**
     * What Gecko gets as the vertical clipping. Gecko already keeps the whole bar (`BrowserEngine.setBarMaxHeight`)
     * out of the layout viewport; a negative value gives the slid-away part back to the visual viewport.
     * Seen on a device: `fixed; bottom:0` then ends at the bar top, expanded or collapsed.
     */
    fun clipping(hidden: Float) = 0f - hidden // not `-hidden`, which is -0f for an expanded bar

    /** Screen y of the top of the bar box, which is where the collapsed strip's tap layer starts. */
    fun stripTop(screenHeight: Float, expanded: Float, hidden: Float) = screenHeight - expanded + hidden

    /** 0 = expanded, 1 = collapsed. */
    fun progress(hidden: Float, maxHidden: Float) = if (maxHidden <= 0f) 1f else (hidden / maxHidden).coerceIn(0f, 1f)
}
