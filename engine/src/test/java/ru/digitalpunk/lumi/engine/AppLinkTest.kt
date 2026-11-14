package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Which top-level navigations Gecko does not load and Lumi offers to open in another app. */
class AppLinkTest {
    @Test fun appSchemesAreAppLinks() {
        for (link in listOf(
            "intent://pay#Intent;scheme=sberpay;package=ru.sberbankmobile;end",
            "bank100000000111://qr.nspk.ru/AS1000",
            "tinkoffbank://Main/PayByMobileNumber",
            "tel:+79990001122",
            "mailto:a@example.com",
            "market://details?id=ru.sberbankmobile",
            "TEL:+7999",
        )) assertEquals(link, appLink(link), link)
    }

    @Test fun schemesGeckoLoadsOrRefusesItselfAreNot() {
        for (link in listOf(
            "https://example.com", "HTTP://example.com", "about:blank", "data:text/html,hi", "blob:https://a/1",
            "javascript:alert(1)", "file:///sdcard/a", "view-source:https://a", "moz-extension://x/a.html",
            "resource://gre/a", "chrome://browser/a", "ws://a", "wss://a", "ftp://a",
        )) assertNull(appLink(link), link)
    }

    @Test fun aLinkWithoutASchemeOrAnEmptyOneIsNot() {
        for (link in listOf("", " ", "example.com", "://x", "1tel:+7", "tel")) assertNull(appLink(link), link)
    }

    @Test fun anOverlongLinkIsNot() {
        assertEquals(MAX_LINK_LENGTH, appLink("tel:" + "1".repeat(MAX_LINK_LENGTH - 4))?.length)
        assertNull(appLink("tel:" + "1".repeat(MAX_LINK_LENGTH - 3)))
    }

    @Test fun anAppSchemeIsAlwaysKeptFromGeckoEvenWhenTooLongToOffer() {
        assertEquals(true, isAppScheme("tel:" + "1".repeat(MAX_LINK_LENGTH)))
        assertEquals(false, isAppScheme("https://example.com"))
    }
}
