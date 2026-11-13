package ru.digitalpunk.lumi.ui

import ru.digitalpunk.lumi.engine.TabId

/** The "New tab opened · Open" snackbar for [tab], a tab opened in the background. */
data class NewTabNotice(val tab: TabId, val private: Boolean)

/** Null once [shown] is done; a newer notice that replaced it meanwhile stays. */
fun NewTabNotice?.clearedBy(shown: NewTabNotice): NewTabNotice? = if (this == shown) null else this
