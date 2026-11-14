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
    /** The page has a painted bar stuck to the screen bottom (a translucent one too), as its content script reported; false until it reports. */
    val hasBottomBar: Boolean = false,
    /** That bar's opaque ARGB colour, as the page's content script reported; null until it reports or when the colour is unknown. */
    val bottomBarColor: Int? = null,
    /**
     * The opaque ARGB colour for the status bar (theme-color, else a header stuck to the top), as the page's content
     * script reported; null until it reports or when the colour is unknown.
     */
    val statusBarColor: Int? = null,
)
