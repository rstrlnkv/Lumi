package ru.digitalpunk.lumi.net

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LnaErrorPageTest {
    @Test fun theTextsAndTheRetryLinkAreOnThePage() {
        val html = lnaErrorHtml("Blocked", "Allow it.", "Try again", "http://192.168.1.1/")
        assertTrue("<h1>Blocked</h1>" in html)
        assertTrue("<p>Allow it.</p>" in html)
        assertTrue("<a href=\"http://192.168.1.1/\">Try again</a>" in html)
        assertFalse("<script" in html)
    }

    @Test fun aHostileAddressCannotBreakOutOfTheLink() {
        val html = lnaErrorHtml("t", "b", "r", "http://x/\"><script>alert(1)</script>&'")
        assertFalse("<script>" in html)
        assertTrue("http://x/&quot;&gt;&lt;script&gt;alert(1)&lt;/script&gt;&amp;&#39;" in html)
    }

    @Test fun aRetryLinkThatIsNotAWebAddressIsLeftOut() {
        assertFalse("<a " in lnaErrorHtml("t", "b", "r", "javascript:alert(1)"))
        assertFalse("<a " in lnaErrorHtml("t", "b", "r", ""))
    }
}
