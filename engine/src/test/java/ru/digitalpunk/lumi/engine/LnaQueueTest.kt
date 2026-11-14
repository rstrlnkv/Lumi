package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LnaQueueTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private fun prompt(uri: String, kind: LnaKind = LnaKind.Network, private: Boolean = false) = lnaPrompt(uri, null, kind, private)

    @Test fun requestsForTheSameSiteKindAndModeShareOneSheet() {
        val q = LnaQueue<String>()
        q.offer(a, prompt("https://x.com/"), "1")
        q.offer(b, prompt("https://x.com/"), "2")
        assertEquals(prompt("https://x.com/"), q.head)
        assertEquals(listOf("1", "2"), q.answer(prompt("https://x.com/").key))
        assertNull(q.head)
    }

    @Test fun aPrivateRequestNeverMergesWithANormalOne() {
        val q = LnaQueue<String>()
        q.offer(a, prompt("https://x.com/"), "normal")
        q.offer(b, prompt("https://x.com/", private = true), "private")
        assertEquals(listOf("normal"), q.answer(prompt("https://x.com/").key))
        assertEquals(prompt("https://x.com/", private = true), q.head)
    }

    @Test fun differentRequestsWaitInArrivalOrderOneAtATime() {
        val q = LnaQueue<String>()
        q.offer(a, prompt("https://x.com/"), "x")
        q.offer(a, prompt("https://y.com/"), "y")
        q.offer(a, prompt("https://x.com/", LnaKind.Device), "xd")
        assertEquals(prompt("https://x.com/"), q.head)
        q.answer(prompt("https://x.com/").key)
        assertEquals(prompt("https://y.com/"), q.head)
        q.answer(prompt("https://y.com/").key)
        assertEquals(prompt("https://x.com/", LnaKind.Device), q.head)
    }

    @Test fun closingOrNavigatingATabCancelsOnlyThatTabsRequests() {
        val q = LnaQueue<String>()
        q.offer(a, prompt("https://x.com/"), "a1")
        q.offer(b, prompt("https://x.com/"), "b1")
        q.offer(a, prompt("https://y.com/"), "a2")
        assertEquals(listOf("a1", "a2"), q.cancelTab(a))
        assertEquals(prompt("https://x.com/"), q.head)
        assertEquals(listOf("b1"), q.cancelTab(b))
        assertNull(q.head)
        assertTrue(q.cancelTab(b).isEmpty())
    }

    @Test fun anAnswerCompletesEachRequestOnce() {
        val q = LnaQueue<String>()
        q.offer(a, prompt("https://x.com/"), "1")
        assertEquals(listOf("1"), q.answer(prompt("https://x.com/").key))
        assertTrue(q.answer(prompt("https://x.com/").key).isEmpty())
        assertTrue(q.cancelTab(a).isEmpty())
    }
}

class LnaQueueEdgeTest {
    private val a = TabId("a")
    private fun prompt(uri: String, kind: LnaKind = LnaKind.Network) = lnaPrompt(uri, null, kind, false)

    @Test fun otherPortsAndSchemesOfTheSameHostAreOtherSites() {
        val q = LnaQueue<String>()
        q.offer(a, prompt("https://x.com/"), "443")
        q.offer(a, prompt("https://x.com:8443/"), "8443")
        q.offer(a, prompt("http://x.com/"), "http")
        assertEquals(listOf("443"), q.answer(prompt("https://x.com/").key))
        assertEquals(listOf("8443"), q.answer(prompt("https://x.com:8443/").key))
        assertEquals(listOf("http"), q.answer(prompt("http://x.com/").key))
    }

    @Test fun answeringAKeyBehindTheHeadLeavesTheHeadOnScreen() {
        val q = LnaQueue<String>()
        q.offer(a, prompt("https://x.com/"), "x")
        q.offer(a, prompt("https://y.com/"), "y")
        assertEquals(listOf("y"), q.answer(prompt("https://y.com/").key))
        assertEquals(prompt("https://x.com/"), q.head)
    }

    @Test fun oneTabAskingTwiceUnderTheSameKeyGetsBothAnswersAndBothCancel() {
        val q = LnaQueue<String>()
        q.offer(a, prompt("https://x.com/"), "1")
        q.offer(a, prompt("https://x.com/"), "2")
        assertEquals(listOf("1", "2"), q.cancelTab(a))
        assertNull(q.head)
    }

    @Test fun aRequestOfferedAfterItsKeyWasAnsweredStartsAFreshSheetAtTheBack() {
        val q = LnaQueue<String>()
        q.offer(a, prompt("https://x.com/"), "1")
        q.offer(a, prompt("https://y.com/"), "y")
        q.answer(prompt("https://x.com/").key)
        q.offer(a, prompt("https://x.com/"), "again")
        assertEquals(prompt("https://y.com/"), q.head)
        q.answer(prompt("https://y.com/").key)
        assertEquals(listOf("again"), q.answer(prompt("https://x.com/").key))
    }
}
