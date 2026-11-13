package ru.digitalpunk.lumi.ui

import ru.digitalpunk.lumi.ui.LinkAction.Copy
import ru.digitalpunk.lumi.ui.LinkAction.Open
import ru.digitalpunk.lumi.ui.LinkAction.OpenInNewPrivateTab
import ru.digitalpunk.lumi.ui.LinkAction.OpenInNewTab
import ru.digitalpunk.lumi.ui.LinkAction.OpenInPrivateTab
import ru.digitalpunk.lumi.ui.LinkAction.Share
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

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
}
