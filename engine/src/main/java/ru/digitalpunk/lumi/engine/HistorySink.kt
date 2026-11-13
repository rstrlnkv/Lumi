package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoSession.HistoryDelegate

/** Where the engine reports visits of normal tabs for global history. Called on the main thread; must not block. */
interface HistorySink {
    fun visited(url: String)
    fun titled(url: String, title: String)
}

private const val NOT_A_PAGE_VISIT =
    HistoryDelegate.VISIT_REDIRECT_SOURCE or HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT or HistoryDelegate.VISIT_UNRECOVERABLE_ERROR

/** A visit is recorded only for a normal tab's top-level page that is not a redirect source and did not fail. */
internal fun shouldReport(isPrivate: Boolean, flags: Int): Boolean =
    !isPrivate && flags and HistoryDelegate.VISIT_TOP_LEVEL != 0 && flags and NOT_A_PAGE_VISIT == 0
