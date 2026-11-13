package ru.digitalpunk.lumi.ui

import ru.digitalpunk.lumi.engine.ContextTarget
import ru.digitalpunk.lumi.engine.ImageTarget
import ru.digitalpunk.lumi.engine.LinkTarget

/** The items of the link and image sheet. */
enum class LinkAction {
    Open, OpenInNewTab, OpenInPrivateTab, OpenInNewPrivateTab, Copy, Share,
    SaveImage, OpenImageInNewTab, OpenImageInNewPrivateTab, CopyImageAddress, ShareImage,
}

/** In a private tab one "Open in new private tab" replaces "Open in new tab" and "Open in private tab". */
fun linkActions(inPrivateTab: Boolean): List<LinkAction> =
    if (inPrivateTab) {
        listOf(LinkAction.Open, LinkAction.OpenInNewPrivateTab, LinkAction.Copy, LinkAction.Share)
    } else {
        listOf(LinkAction.Open, LinkAction.OpenInNewTab, LinkAction.OpenInPrivateTab, LinkAction.Copy, LinkAction.Share)
    }

/** The image items; a private tab opens the image in a private tab only. */
fun imageActions(inPrivateTab: Boolean, withShare: Boolean): List<LinkAction> = buildList {
    add(LinkAction.SaveImage)
    add(if (inPrivateTab) LinkAction.OpenImageInNewPrivateTab else LinkAction.OpenImageInNewTab)
    add(LinkAction.CopyImageAddress)
    if (withShare) add(LinkAction.ShareImage)
}

/** The sheet's item groups, with a divider between them: an image inside a link shows the link items first and one Share only. */
fun sheetGroups(hasLink: Boolean, hasImage: Boolean, inPrivateTab: Boolean): List<List<LinkAction>> = when {
    hasLink && hasImage -> listOf(linkActions(inPrivateTab), imageActions(inPrivateTab, withShare = false))
    hasLink -> listOf(linkActions(inPrivateTab))
    hasImage -> listOf(imageActions(inPrivateTab, withShare = true))
    else -> emptyList()
}

/** What a sheet item acts on: a [Link] (the link, or the image's address for the image items), or a [Save] of the image. */
sealed interface SheetStep {
    data class Link(val link: LinkTarget) : SheetStep
    data class Save(val image: ImageTarget) : SheetStep
}

/** Save image saves the image; the other image items act on its address as a link; the link items act on the link. */
fun sheetStep(target: ContextTarget, action: LinkAction): SheetStep? {
    val image = target.image
    return when {
        image == null || action !in imageActions(target.inPrivateTab, withShare = true) -> target.link?.let(SheetStep::Link)
        action == LinkAction.SaveImage -> SheetStep.Save(image)
        else -> SheetStep.Link(LinkTarget(target.tab, image.url, image.title, image.inPrivateTab))
    }
}

/** How a link opens in a new tab: [select] it at once or keep it in the background with a notice; a private one passes the lock first. */
data class LinkOpen(val private: Boolean, val select: Boolean, val showNotice: Boolean, val needsGate: Boolean)

/** Null for the actions that open no tab. */
fun linkOpen(action: LinkAction, switchToNewTabs: Boolean): LinkOpen? {
    val private = when (action) {
        LinkAction.OpenInNewTab, LinkAction.OpenImageInNewTab -> false
        LinkAction.OpenInPrivateTab, LinkAction.OpenInNewPrivateTab, LinkAction.OpenImageInNewPrivateTab -> true
        LinkAction.Open, LinkAction.Copy, LinkAction.Share, LinkAction.SaveImage, LinkAction.CopyImageAddress, LinkAction.ShareImage -> return null
    }
    return LinkOpen(private, select = switchToNewTabs, showNotice = !switchToNewTabs, needsGate = private)
}
