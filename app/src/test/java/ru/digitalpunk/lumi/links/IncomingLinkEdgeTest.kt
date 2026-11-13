package ru.digitalpunk.lumi.links

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inputs another app can send that the first test file did not feed. */
class IncomingLinkEdgeTest {
    private val prefix = "https://search.test/?q="
    private fun view(data: String?) = incomingUrl(IncomingIntent("android.intent.action.VIEW", data, null), prefix)
    private fun search(query: String?) = incomingUrl(IncomingIntent("android.intent.action.WEB_SEARCH", null, query), prefix)

    @Test fun viewTrimsSurroundingWhitespaceAndNewlines() {
        assertEquals("https://a.test/", view("  \nhttps://a.test/\t\n"))
    }

    @Test fun viewRefusesSchemesHiddenBehindWhitespaceOrCase() {
        listOf(" javascript:alert(1)", "JaVaScRiPt:alert(1)", "\u0000https://a.test/", "https:/a.test/", "https//a.test/", "httpx://a.test/", "view-source:https://a.test/")
            .forEach { assertNull(view(it), it) }
    }

    @Test fun viewRefusesAnyEmbeddedControlCharacter() {
        listOf("\u0000", "\u001B", "\u007F", "\u0085", "\r").forEach { c -> assertNull(view("https://a.test/a${c}b"), c.toString()) }
    }

    @Test fun webSearchNeverTurnsAnUnsafeSchemeIntoALoad() {
        listOf(
            "file:///etc/passwd", "content://media/external/1", "intent://x#Intent;scheme=http;end", "data:text/html,<script>1</script>",
            "view-source:https://a.test/", "chrome://settings", "about:config", "resource://gre/x", "blob:https://a.test/1",
        ).forEach { q ->
            val url = search(q)
            assertTrue(url == null || url.startsWith(prefix), "$q -> $url")
        }
    }

    @Test fun webSearchWithAControlCharacterInAnAddressLoadsNothingOrSearches() {
        val url = search("a.test/\u0007x")
        assertTrue(url == null || url.startsWith(prefix), "$url")
    }

    @Test fun webSearchKeepsTheEngineWhenTheTextHasUnicodeAndSymbols() {
        assertEquals(prefix + "%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82+%26+%23", search("привет & #"))
    }
}
