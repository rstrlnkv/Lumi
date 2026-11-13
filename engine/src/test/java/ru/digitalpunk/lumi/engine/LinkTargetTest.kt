package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LinkTargetTest {
    @Test fun onlyHttpAndHttpsPass() {
        assertEquals("http://a.com/x", webLink("http://a.com/x"))
        assertEquals("https://a.com/", webLink("https://a.com/"))
        listOf(
            "javascript:alert(1)", "JaVaScRiPt:alert(1)", "data:text/html,<b>x</b>", "mailto:a@b.c", "tel:123",
            "blob:https://x", "file:///etc/hosts", "about:blank", null, "", "   ", "https://", "http://",
        ).forEach { assertNull(webLink(it), "link $it") }
    }

    @Test fun upperCaseSchemeWithLeadingSpacePassesTrimmed() {
        assertEquals("HTTPS://A.com/", webLink("  HTTPS://A.com/ "))
    }

    @Test fun urlOneCharOverTheLimitIsRefused() {
        val base = "https://a.com/"
        val atLimit = base + "x".repeat(MAX_LINK_LENGTH - base.length)
        assertEquals(atLimit, webLink(atLimit))
        assertNull(webLink(atLimit + "x"))
    }

    @Test fun titleAttributeBeatsLinkText() {
        assertEquals("Title", linkTitle("Title", "Text"))
    }

    @Test fun blankTitleFallsBackToLinkText() {
        assertEquals("Text", linkTitle(" \n ", "Text"))
        assertEquals("Text", linkTitle(null, "Text"))
    }

    @Test fun bothBlankGivesNoTitle() {
        assertNull(linkTitle(null, null))
        assertNull(linkTitle("", "\t "))
    }

    @Test fun whitespaceInTitleIsCollapsed() {
        assertEquals("Read the full story", linkTitle(null, "  Read\n the\t\tfull   story "))
    }

    @Test fun longTitleIsCut() {
        assertEquals(MAX_LINK_TITLE, linkTitle("x".repeat(MAX_LINK_TITLE + 1), null)?.length)
        assertEquals(MAX_LINK_TITLE, linkTitle("x".repeat(MAX_LINK_TITLE), null)?.length)
    }
}
