package ru.digitalpunk.lumi.links

import kotlin.test.Test
import kotlin.test.assertEquals

class AppLinkBackTest {
    @Test fun pageHistoryWinsOverTheAppMark() {
        assertEquals(BackStep.PageBack, backStep(canGoBack = true, openedByApp = true))
        assertEquals(BackStep.PageBack, backStep(canGoBack = true, openedByApp = false))
    }

    @Test fun firstPageOfATabFromAnAppReturnsToTheApp() {
        assertEquals(BackStep.ReturnToApp, backStep(canGoBack = false, openedByApp = true))
    }

    @Test fun firstPageOfAnOrdinaryTabHasNoBack() {
        assertEquals(BackStep.None, backStep(canGoBack = false, openedByApp = false))
    }
}
