package ru.digitalpunk.lumi.engine

/** What Gecko says about the page under a finger that just went down. No GeckoView types. */
data class PageTouchInfo(
    /** The browser (not the page's own script) scrolls or zooms with this touch. */
    val browserHandles: Boolean,
    /** The page can scroll toward its top. */
    val canScrollUp: Boolean,
    /** The page can scroll toward its bottom. */
    val canScrollDown: Boolean,
    val canOverscrollVertically: Boolean,
) {
    val canPullToRefresh get() = browserHandles && !canScrollUp && canOverscrollVertically

    companion object {
        // Values of PanZoomController.INPUT_RESULT_HANDLED, SCROLLABLE_FLAG_TOP/BOTTOM, OVERSCROLL_FLAG_VERTICAL.
        private const val HANDLED = 1
        private const val SCROLLABLE_TOP = 1
        private const val SCROLLABLE_BOTTOM = 4
        private const val OVERSCROLL_VERTICAL = 2

        fun fromFlags(handled: Int, scrollable: Int, overscroll: Int) = PageTouchInfo(
            browserHandles = handled == HANDLED,
            canScrollUp = scrollable and SCROLLABLE_TOP != 0,
            canScrollDown = scrollable and SCROLLABLE_BOTTOM != 0,
            canOverscrollVertically = overscroll and OVERSCROLL_VERTICAL != 0,
        )
    }
}

/** Finger events over the page, on the main thread. The engine never consumes or changes them. */
interface PageTouchListener {
    fun onTouchDown()

    /** Arrives later than [onTouchDown]; dropped if a newer gesture started meanwhile. */
    fun onTouchDownResolved(info: PageTouchInfo)

    /** First pointer only; `+` is down; since the last event. */
    fun onTouchMove(dyPx: Float)

    /** Finger up, cancel, or a second finger down (then [cancelled] is true). */
    fun onTouchEnd(cancelled: Boolean)
}
