package ru.digitalpunk.lumi.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The intake cap keeps one char over each limit, so an oversize link or query is refused, never truncated and accepted. */
class IncomingIntakeTest {
    private val prefix = "https://search.test/?q="

    @Test fun oversizeViewLinkIsRefusedNotTruncatedToTheLimit() {
        val link = "https://a.test/" + "a".repeat(40_000 - 15)
        assertEquals(40_000, link.length)
        assertNull(incomingUrl(incomingIntent("android.intent.action.VIEW", link, null), prefix))
    }

    @Test fun oversizeWebSearchQueryIsRefusedNotTruncatedToTheLimit() {
        assertNull(incomingUrl(incomingIntent("android.intent.action.WEB_SEARCH", null, "q".repeat(40_000)), prefix))
    }

    @Test fun linkAtTheLimitPassesIntakeWhole() {
        val link = "https://a.test/" + "a".repeat(32_768 - 15)
        assertEquals(link, incomingUrl(incomingIntent("android.intent.action.VIEW", link, null), prefix))
    }
}
