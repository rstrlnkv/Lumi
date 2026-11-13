package ru.digitalpunk.lumi.suggest

/** One row of the address bar's suggestion list. */
sealed interface Suggestion {
    /** A search query from the engine; it always searches, even when it looks like an address. */
    data class Search(val text: String) : Suggestion

    data class History(val title: String, val url: String) : Suggestion

    data class Bookmark(val title: String, val url: String) : Suggestion
}
