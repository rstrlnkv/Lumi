package ru.digitalpunk.lumi.engine

/** What the UI needs to know about the page on screen. */
data class PageState(
    val url: String = "about:blank",
    val isLoading: Boolean = false,
    val progress: Int = 0, // 0..100
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isCrashed: Boolean = false,
    /** Distinct trackers blocked on this page load; 0 again when a new load starts. */
    val blockedTrackers: Int = 0,
    /** The tab's back history for the Back long-press menu; empty after a crash. */
    val history: BackHistory = BackHistory(),
    /** The page has a bar stuck to the screen bottom, as its content script reported; false until it reports. */
    val hasBottomBar: Boolean = false,
)
