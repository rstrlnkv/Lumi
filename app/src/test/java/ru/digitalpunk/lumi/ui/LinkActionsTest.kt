package ru.digitalpunk.lumi.ui

import ru.digitalpunk.lumi.engine.ContextTarget
import ru.digitalpunk.lumi.engine.ImageTarget
import ru.digitalpunk.lumi.engine.LinkTarget
import ru.digitalpunk.lumi.engine.TabId
import ru.digitalpunk.lumi.ui.LinkAction.Copy
import ru.digitalpunk.lumi.ui.LinkAction.CopyImageAddress
import ru.digitalpunk.lumi.ui.LinkAction.Open
import ru.digitalpunk.lumi.ui.LinkAction.OpenImageInNewPrivateTab
import ru.digitalpunk.lumi.ui.LinkAction.OpenImageInNewTab
import ru.digitalpunk.lumi.ui.LinkAction.OpenInNewPrivateTab
import ru.digitalpunk.lumi.ui.LinkAction.OpenInNewTab
import ru.digitalpunk.lumi.ui.LinkAction.OpenInPrivateTab
import ru.digitalpunk.lumi.ui.LinkAction.SaveImage
import ru.digitalpunk.lumi.ui.LinkAction.Share
import ru.digitalpunk.lumi.ui.LinkAction.ShareImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkActionsTest {
    @Test fun normalTabOffersFiveActionsInOrder() {
        assertEquals(listOf(Open, OpenInNewTab, OpenInPrivateTab, Copy, Share), linkActions(inPrivateTab = false))
    }

    @Test fun privateTabOffersOneNewTabItemAndNoPrivateTabItem() {
        assertEquals(listOf(Open, OpenInNewPrivateTab, Copy, Share), linkActions(inPrivateTab = true))
    }

    @Test fun linkOpenInNewTabIsBackgroundWithNoticeByDefault() {
        assertEquals(LinkOpen(private = false, select = false, showNotice = true, needsGate = false), linkOpen(OpenInNewTab, switchToNewTabs = false))
    }

    @Test fun linkOpenWithSwitchOnSelectsAndShowsNoNotice() {
        assertEquals(LinkOpen(private = false, select = true, showNotice = false, needsGate = false), linkOpen(OpenInNewTab, switchToNewTabs = true))
        assertEquals(LinkOpen(private = true, select = true, showNotice = false, needsGate = true), linkOpen(OpenInPrivateTab, switchToNewTabs = true))
    }

    @Test fun privateOpenNeedsTheGateInBothSettings() {
        for (action in listOf(OpenInPrivateTab, OpenInNewPrivateTab)) {
            for (switch in listOf(false, true)) {
                val plan = linkOpen(action, switch)!!
                assertEquals(true, plan.private, "$action $switch")
                assertEquals(true, plan.needsGate, "$action $switch")
                assertEquals(switch, plan.select, "$action $switch")
                assertEquals(!switch, plan.showNotice, "$action $switch")
            }
        }
    }

    @Test fun copyShareAndOpenHaveNoOpenPlan() {
        for (switch in listOf(false, true)) listOf(Open, Copy, Share).forEach { assertNull(linkOpen(it, switch), "$it $switch") }
    }

    @Test fun imageOnlyNormalTabOffersSaveOpenCopyShareInOrder() {
        assertEquals(listOf(listOf(SaveImage, OpenImageInNewTab, CopyImageAddress, ShareImage)), sheetGroups(hasLink = false, hasImage = true, inPrivateTab = false))
    }

    @Test fun imageOnlyPrivateTabOpensInANewPrivateTab() {
        assertEquals(listOf(listOf(SaveImage, OpenImageInNewPrivateTab, CopyImageAddress, ShareImage)), sheetGroups(hasLink = false, hasImage = true, inPrivateTab = true))
    }

    @Test fun linkedImageShowsLinkItemsThenImageItemsWithOneShare() {
        val groups = sheetGroups(hasLink = true, hasImage = true, inPrivateTab = false)
        assertEquals(listOf(listOf(Open, OpenInNewTab, OpenInPrivateTab, Copy, Share), listOf(SaveImage, OpenImageInNewTab, CopyImageAddress)), groups)
        assertEquals(1, groups.flatten().count { it == Share })
        assertTrue(ShareImage !in groups.flatten())
    }

    @Test fun linkedImagePrivateKeepsM5PrivateItemsAndPrivateImageOpen() {
        assertEquals(
            listOf(listOf(Open, OpenInNewPrivateTab, Copy, Share), listOf(SaveImage, OpenImageInNewPrivateTab, CopyImageAddress)),
            sheetGroups(hasLink = true, hasImage = true, inPrivateTab = true),
        )
    }

    @Test fun linkOnlyIsUnchanged() {
        for (private in listOf(false, true)) assertEquals(listOf(linkActions(private)), sheetGroups(hasLink = true, hasImage = false, inPrivateTab = private))
    }

    @Test fun neitherLinkNorImageIsEmpty() {
        for (private in listOf(false, true)) assertEquals(emptyList(), sheetGroups(hasLink = false, hasImage = false, inPrivateTab = private))
    }

    @Test fun imageActionsPlanOpensInTheTabKind() {
        for (switch in listOf(false, true)) {
            assertEquals(LinkOpen(private = false, select = switch, showNotice = !switch, needsGate = false), linkOpen(OpenImageInNewTab, switch))
            assertEquals(LinkOpen(private = true, select = switch, showNotice = !switch, needsGate = true), linkOpen(OpenImageInNewPrivateTab, switch))
            listOf(SaveImage, CopyImageAddress, ShareImage).forEach { assertNull(linkOpen(it, switch), "$it $switch") }
        }
    }

    @Test fun everyCombinationHasNoEmptyGroupNoRepeatAndAnImageOnlySheetHasNoLinkShare() {
        for (link in listOf(false, true)) for (image in listOf(false, true)) for (private in listOf(false, true)) {
            val groups = sheetGroups(link, image, private)
            val all = groups.flatten()
            assertTrue(groups.none { it.isEmpty() }, "$link $image $private")
            assertEquals(all.distinct(), all, "$link $image $private")
            if (!link) assertTrue(Share !in all, "$image $private")
        }
    }

    private val tab = TabId("t")

    private fun target(link: Boolean, private: Boolean) = ContextTarget(
        tab, private, LinkTarget(tab, "https://x/a", "A", private).takeIf { link }, ImageTarget("https://x/b.png", "B", "x", private),
    )

    @Test fun saveImageSavesTheImageWithTheTabsPrivateFlagAndNeverActsAsALink() {
        for (link in listOf(false, true)) for (private in listOf(false, true)) {
            val step = sheetStep(target(link, private), SaveImage)
            assertEquals(SheetStep.Save(ImageTarget("https://x/b.png", "B", "x", private)), step, "$link $private")
        }
    }

    @Test fun theOtherImageItemsActOnTheImageAddressAsALinkOfTheSameTabKind() {
        for (private in listOf(false, true)) (imageActions(private, withShare = true) - SaveImage).forEach {
            assertEquals(SheetStep.Link(LinkTarget(tab, "https://x/b.png", "B", private)), sheetStep(target(link = false, private), it), "$it $private")
        }
    }

    @Test fun linkItemsOnALinkedImageActOnTheLink() {
        listOf(Open, OpenInNewTab, OpenInPrivateTab, Copy, Share).forEach {
            assertEquals(SheetStep.Link(LinkTarget(tab, "https://x/a", "A", false)), sheetStep(target(link = true, private = false), it), "$it")
        }
    }
}
