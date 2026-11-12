package ru.digitalpunk.lumi.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectionHostsTest {
    @Test fun toggledAddsAHost() = assertEquals(setOf("a.com"), emptySet<String>().toggled("a.com", off = true))

    @Test fun toggledRemovesAHost() = assertEquals(setOf("b.com"), setOf("a.com", "b.com").toggled("a.com", off = false))

    @Test fun addingTheSameHostTwiceKeepsOneEntry() = assertEquals(setOf("a.com"), setOf("a.com").toggled("a.com", off = true))

    @Test fun hostsAreNormalisedForCaseAndWww() {
        assertEquals(setOf("a.com"), emptySet<String>().toggled("WWW.A.com", off = true))
        assertEquals(emptySet(), setOf("a.com").toggled("www.a.com", off = false))
    }

    @Test fun blankOrOverlongHostIsRejected() {
        assertEquals(emptySet(), emptySet<String>().toggled("  ", off = true))
        assertEquals(emptySet(), emptySet<String>().toggled("a".repeat(254), off = true))
    }

    @Test fun setIsCappedAt500Hosts() {
        val full = (1..500).map { "s$it.com" }.toSet()
        assertEquals(full, full.toggled("one-more.com", off = true))
        assertEquals(499, full.toggled("s1.com", off = false).size)
    }
}
