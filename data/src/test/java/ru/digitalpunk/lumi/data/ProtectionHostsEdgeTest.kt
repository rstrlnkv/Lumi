package ru.digitalpunk.lumi.data

import kotlin.test.Test
import kotlin.test.assertEquals

class ProtectionHostsEdgeTest {
    @Test fun exceptionForABareDomainDoesNotCoverItsSubdomain() {
        val set = emptySet<String>().toggled("example.com", off = true)
        assertEquals(setOf("example.com"), set)
        assertEquals(false, "sub.example.com" in set)
    }

    @Test fun uppercaseHostIsStoredLowerCase() =
        assertEquals(setOf("example.com"), emptySet<String>().toggled("EXAMPLE.COM", off = true))

    @Test fun uppercaseHostRemovesTheLowerCaseEntry() =
        assertEquals(emptySet(), setOf("example.com").toggled("EXAMPLE.COM", off = false))

    @Test fun trailingDotHostIsTheSameSiteAsWithout() =
        assertEquals(setOf("example.com"), emptySet<String>().toggled("example.com.", off = true))

    @Test fun trailingDotHostRemovesTheEntryWithout() =
        assertEquals(emptySet(), setOf("example.com").toggled("example.com.", off = false))

    @Test fun surroundingWhitespaceIsTrimmed() =
        assertEquals(setOf("a.com"), emptySet<String>().toggled("  A.com \n", off = true))

    @Test fun removingAHostThatIsNotThereChangesNothing() =
        assertEquals(setOf("a.com"), setOf("a.com").toggled("b.com", off = false))

    @Test fun togglingOnThenOffThenOnEndsOn() {
        val s = emptySet<String>().toggled("a.com", true).toggled("a.com", false).toggled("a.com", true)
        assertEquals(setOf("a.com"), s)
    }

    @Test fun offTwiceInARowLeavesItOff() =
        assertEquals(emptySet(), setOf("a.com").toggled("a.com", false).toggled("a.com", false))

    @Test fun localhostAndIpAreAcceptedAsHosts() =
        assertEquals(setOf("localhost", "192.168.0.1"), emptySet<String>().toggled("localhost", true).toggled("192.168.0.1", true))

    @Test fun punycodeHostIsKept() =
        assertEquals(setOf("xn--e1afmkfd.xn--p1ai"), emptySet<String>().toggled("XN--e1afmkfd.xn--p1ai", true))

    @Test fun hostOfExactly253CharsIsAccepted() {
        val host = "a".repeat(253)
        assertEquals(setOf(host), emptySet<String>().toggled(host, off = true))
    }

    @Test fun removalStillWorksWhenTheSetIsFull() {
        val full = (1..500).map { "s$it.com" }.toSet()
        assertEquals(499, full.toggled("s7.com", off = false).size)
        assertEquals(full, full.toggled("S7.com", off = true))
    }
}
