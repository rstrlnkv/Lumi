package ru.digitalpunk.lumi.engine

/**
 * What the page-end extension needs from the app: the collapsed strip's height and the page view's width,
 * both in px. [update] gives a pair only when it is valid and new, so the port is not flooded.
 */
internal class PageEndSpacer {
    /** The last valid pair; a port that connects later gets it. */
    var current: Pair<Int, Int>? = null
        private set

    fun update(stripPx: Int, viewWidthPx: Int): Pair<Int, Int>? {
        if (stripPx <= 0 || viewWidthPx <= 0) return null
        val pair = stripPx to viewWidthPx
        if (pair == current) return null
        current = pair
        return pair
    }

    companion object {
        /** Must equal `browser_specific_settings.gecko.id` in the extension's manifest. */
        const val ID = "page-end@lumi.digitalpunk.ru"
        const val URI = "resource://android/assets/lumi-page-end/"

        /** The name `background.js` passes to `connectNative`. */
        const val NATIVE_APP = "lumiPageEnd"
    }
}
