package ru.digitalpunk.lumi.ui.tabgrid

import ru.digitalpunk.lumi.address.displayUrl

/** The page title, else the host, else [untitled]. */
fun cardTitle(title: String, url: String, untitled: String): String =
    title.takeIf { it.isNotBlank() } ?: displayUrl(url).text.ifEmpty { untitled }

/** The first non-blank character (a whole code point, so an emoji stays whole), upper-cased, stands in for a favicon. */
fun cardInitial(title: String): String {
    val text = title.trimStart()
    return if (text.isEmpty()) "" else text.substring(0, Character.charCount(text.codePointAt(0))).uppercase()
}

enum class CloseEffect { Stay, LeaveGridForNewTab }

/** Closing the last tab opens a new empty one, and the grid gives way to it. */
fun closeEffect(replacementCreated: Boolean) = if (replacementCreated) CloseEffect.LeaveGridForNewTab else CloseEffect.Stay

/** The number inside the Tabs button; 100 and more would not fit, so they show as infinity. */
fun tabCountGlyph(count: Int): String = if (count >= 100) "∞" else count.toString()

/** Half of the card's width: a swipe that far closes it. */
const val DISMISS_FRACTION = 0.5f

/** The card fades from 1 to [minAlpha] as its [offset] (px) reaches the dismiss point, so the fade marks where letting go closes it. */
fun swipeAlpha(offset: Float, width: Float, minAlpha: Float): Float {
    if (width <= 0f) return 1f
    val reached = (kotlin.math.abs(offset) / (DISMISS_FRACTION * width)).coerceIn(0f, 1f)
    return 1f - (1f - minAlpha) * reached
}
