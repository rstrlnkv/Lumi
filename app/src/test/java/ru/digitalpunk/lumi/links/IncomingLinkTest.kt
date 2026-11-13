package ru.digitalpunk.lumi.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IncomingLinkTest {
    private val prefix = "https://search.test/?q="
    private fun view(data: String?) = incomingUrl(IncomingIntent(VIEW, data, null), prefix)
    private fun search(query: String?, data: String? = null) = incomingUrl(IncomingIntent(WEB_SEARCH, data, query), prefix)

    @Test fun viewOpensHttpAndHttpsInAnyCase() {
        assertEquals("http://a.test/", view("http://a.test/"))
        assertEquals("HTTPS://A.test/x", view("HTTPS://A.test/x"))
    }

    @Test fun viewRefusesEverySchemeButHttpAndHttps() {
        listOf(
            "file:///sdcard/x.html", "content://media/1", "javascript:alert(1)", "intent://x#Intent;end", "data:text/html,hi",
            "about:blank", "market://details?id=x", "ftp://a.test/", "https://",
        ).forEach { assertNull(view(it), it) }
    }

    @Test fun viewAcceptsTheLongestLinkAndRefusesOneCharMore() {
        val base = "https://a.test/"
        assertEquals(32_768, view(base + "a".repeat(32_768 - base.length))?.length)
        assertNull(view(base + "a".repeat(32_769 - base.length)))
    }

    @Test fun viewRefusesControlCharactersBlankAndNull() {
        assertNull(view("https://a.test/\u0007x"))
        assertNull(view("https://a.test/\nx"))
        assertNull(view("   "))
        assertNull(view(null))
    }

    @Test fun otherActionsAreIgnored() {
        assertNull(incomingUrl(IncomingIntent("android.intent.action.SEND", "https://a.test/", "q"), prefix))
        assertNull(incomingUrl(IncomingIntent("android.intent.action.MAIN", "https://a.test/", null), prefix))
        assertNull(incomingUrl(IncomingIntent(null, "https://a.test/", null), prefix))
    }

    @Test fun webSearchOpensAnAddress() {
        assertEquals("https://example.com/x", search("https://example.com/x"))
        assertEquals("https://example.com", search("example.com"))
    }

    @Test fun webSearchSearchesTextWithTheChosenEngine() {
        assertEquals(prefix + "kotlin+coroutines", search("kotlin coroutines"))
        assertEquals(prefix + "kotlin", search("kotlin"))
    }

    @Test fun webSearchNeverOpensAboutOrJavascript() {
        assertNull(search("about:blank"))
        assertEquals(prefix + "javascript%3Aalert%28document.domain%29", search("javascript:alert(document.domain)"))
    }

    @Test fun webSearchRefusesBlankAndOverlongQueries() {
        assertNull(search("  "))
        assertNull(search(null))
        assertEquals(prefix + "a".repeat(2_048), search("a".repeat(2_048)))
        assertNull(search("a".repeat(2_049)))
    }

    @Test fun webSearchIgnoresTheData() {
        assertNull(search(null, data = "https://a.test/"))
    }

    private companion object {
        const val VIEW = "android.intent.action.VIEW"
        const val WEB_SEARCH = "android.intent.action.WEB_SEARCH"
    }
}
