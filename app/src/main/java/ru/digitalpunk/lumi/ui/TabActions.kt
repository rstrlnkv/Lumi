package ru.digitalpunk.lumi.ui

import ru.digitalpunk.lumi.engine.TabId

/** What the bars need from the tabs: how many there are, the hosts of the neighbours (null at either end), the Back rule and six actions. */
class TabActions(
    val selectedId: TabId?,
    val count: Int,
    val prevHost: String?,
    val nextHost: String?,
    val newTab: () -> Unit,
    val newPrivateTab: () -> Unit,
    val openGrid: () -> Unit,
    /** +1 for the next tab, -1 for the previous one. */
    val switchBy: (Int) -> Unit,
    /** Closes the selected tab; the last one leaves a new blank tab. */
    val closeTab: () -> Unit,
    /** The first page of a tab another app opened: Back closes it and returns to that app. */
    val returnsToApp: Boolean,
    /** The bar's Back and the system Back: the page's history, or the return to the app. */
    val goBack: () -> Unit,
)

/** What the More menu needs from the library: whether the page on screen is saved, whether it can be, and two actions. */
class LibraryActions(
    val bookmarked: Boolean,
    val canBookmark: Boolean,
    /** Saves the page into the start-page folder, or opens the editor when it is saved already. */
    val addOrEditBookmark: () -> Unit,
    val openLibrary: () -> Unit,
)
