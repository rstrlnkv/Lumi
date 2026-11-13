package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Link rules pinned with literal numbers, so a changed constant cannot move the test with it. */
class LinkTargetEdgeTest {
    @Test fun titleIsCutAtExactly200CharsNotMore() {
        assertEquals(200, linkTitle("x".repeat(201), null)?.length)
        assertEquals(200, linkTitle("x".repeat(200), null)?.length)
        assertEquals(199, linkTitle("x".repeat(199), null)?.length)
        assertEquals(200, linkTitle(null, "y".repeat(5000))?.length)
    }

    @Test fun urlLimitIs32768CharsLiterally() {
        val base = "https://a.com/"
        assertEquals(32_768, (base + "x".repeat(32_768 - base.length)).length)
        assertEquals(32_768, webLink(base + "x".repeat(32_768 - base.length))?.length)
        assertNull(webLink(base + "x".repeat(32_769 - base.length)))
    }

    @Test fun cutTitleNeverEndsInHalfOfAnEmoji() {
        val cut = linkTitle("x" + "😀".repeat(100), null)!!
        assertFalse(Character.isHighSurrogate(cut.last()), "title ends in a lone high surrogate")
    }

    @Test fun hugeLinkTextIsHandledAndCut() {
        val cut = linkTitle(null, "word \n".repeat(500_000))
        assertTrue(cut != null && cut.length == 200 && "\n" !in cut)
    }

    @Test fun schemeBrokenByControlCharsOrSpacesIsRefused() {
        listOf("java\nscript:alert(1)", "\u0000http://a.com", "ht tp://a.com", "http//a.com", "http:/a.com", "https:a.com", "//a.com", "  data:text/html,x ")
            .forEach { assertNull(webLink(it), "link $it") }
    }

    @Test fun blankAndImageOnlyElementsGiveNoLink() {
        assertNull(webLink(null))
        assertNull(webLink(""))
        assertNull(webLink("\n\t "))
    }
}
