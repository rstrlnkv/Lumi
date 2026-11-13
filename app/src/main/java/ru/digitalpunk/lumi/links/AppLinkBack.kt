package ru.digitalpunk.lumi.links

enum class BackStep { PageBack, ReturnToApp, None }

/** What Back does: the page's history first; on the first page of a tab another app opened, close it and return to that app. */
fun backStep(canGoBack: Boolean, openedByApp: Boolean): BackStep = when {
    canGoBack -> BackStep.PageBack
    openedByApp -> BackStep.ReturnToApp
    else -> BackStep.None
}
