package ru.digitalpunk.lumi.engine

/** What the UI needs to know about the page on screen. */
data class PageState(
    val url: String = "about:blank",
    val isLoading: Boolean = false,
    val progress: Int = 0, // 0..100
    val canGoBack: Boolean = false,
    val isCrashed: Boolean = false,
)
