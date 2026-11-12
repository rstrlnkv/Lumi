package ru.digitalpunk.lumi.gesture

/** How far the address bar is slid off the screen, in px: 0 = fully shown, [maxHidden] = collapsed to its strip. */
class BarCollapse(maxHidden: Float) {
    var maxHidden = maxHidden
        private set
    var hidden = 0f
        private set
    private var collapsing = true

    val isExpanded get() = hidden <= 0f
    val isCollapsed get() = hidden >= maxHidden

    /** Moves the bar by the finger: up (`dy < 0`) hides, down reveals. */
    fun drag(dy: Float) {
        if (dy.isNaN()) return
        if (dy != 0f) collapsing = dy < 0
        hidden = (hidden - dy).coerceIn(0f, maxHidden)
    }

    /** Where to settle after a drag, by the last direction of the finger: 0 or [maxHidden]. */
    fun settleTarget(): Float = when {
        collapsing -> if (hidden > 0.2f * maxHidden) maxHidden else 0f
        else -> if (hidden < 0.8f * maxHidden) 0f else maxHidden
    }

    fun snapTo(value: Float) {
        hidden = value.coerceIn(0f, maxHidden)
    }

    fun setMax(value: Float) {
        maxHidden = value.coerceAtLeast(0f)
        hidden = hidden.coerceIn(0f, maxHidden)
    }
}
