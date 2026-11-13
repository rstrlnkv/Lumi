package ru.digitalpunk.lumi.ui

/** The items of the link sheet. */
enum class LinkAction { Open, OpenInNewTab, OpenInPrivateTab, OpenInNewPrivateTab, Copy, Share }

/** In a private tab one "Open in new private tab" replaces "Open in new tab" and "Open in private tab". */
fun linkActions(inPrivateTab: Boolean): List<LinkAction> =
    if (inPrivateTab) {
        listOf(LinkAction.Open, LinkAction.OpenInNewPrivateTab, LinkAction.Copy, LinkAction.Share)
    } else {
        listOf(LinkAction.Open, LinkAction.OpenInNewTab, LinkAction.OpenInPrivateTab, LinkAction.Copy, LinkAction.Share)
    }

/** How a link opens in a new tab: [select] it at once or keep it in the background with a notice; a private one passes the lock first. */
data class LinkOpen(val private: Boolean, val select: Boolean, val showNotice: Boolean, val needsGate: Boolean)

/** Null for the actions that open no tab. */
fun linkOpen(action: LinkAction, switchToNewTabs: Boolean): LinkOpen? {
    val private = when (action) {
        LinkAction.OpenInNewTab -> false
        LinkAction.OpenInPrivateTab, LinkAction.OpenInNewPrivateTab -> true
        LinkAction.Open, LinkAction.Copy, LinkAction.Share -> return null
    }
    return LinkOpen(private, select = switchToNewTabs, showNotice = !switchToNewTabs, needsGate = private)
}
