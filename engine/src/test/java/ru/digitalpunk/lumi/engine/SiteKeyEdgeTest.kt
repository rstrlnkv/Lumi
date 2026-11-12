package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SiteKeyEdgeTest {
    @Test fun trailingDotHostGivesTheSameKeyAsTheHostWithout() =
        assertEquals(siteKey("https://example.com/"), siteKey("https://example.com./"))

    @Test fun trailingDotWithWwwAndPortGivesTheBareHost() =
        assertEquals("example.com", siteKey("https://www.example.com.:8443/x"))

    @Test fun subdomainKeepsItsOwnKey() = assertEquals("sub.example.com", siteKey("https://sub.example.com/"))

    @Test fun punycodeHostIsKeptAsIs() = assertEquals("xn--e1afmkfd.xn--p1ai", siteKey("https://XN--E1AFMKFD.xn--p1ai/"))

    @Test fun ipv4HostKeepsAllFourPartsAndDropsThePort() = assertEquals("192.168.0.1", siteKey("http://192.168.0.1:8080/admin"))

    @Test fun localhostIsAKey() = assertEquals("localhost", siteKey("http://localhost:3000/"))

    @Test fun hostThatIsOnlyWwwDotIsNull() = assertNull(siteKey("https://www./"))

    @Test fun hostStartingWithWwwButNoDotKeepsIt() = assertEquals("wwwexample.com", siteKey("https://wwwexample.com/"))

    @Test fun emptyPortIsDropped() = assertEquals("example.com", siteKey("https://example.com:/"))

    @Test fun uppercaseSchemeStillCounts() = assertEquals("example.com", siteKey("HTTPS://EXAMPLE.com/"))

    @Test fun ipv6WithoutBracketsAfterUserinfoIsNull() = assertNull(siteKey("https://user@[2001:db8::1]:443/"))

    @Test fun nonNumericPortGivesNull() = assertNull(siteKey("https://example.com:abc/"))

    @Test fun bracketedHostWithoutAColonGivesNull() = assertNull(siteKey("https://[abc]/"))

    @Test fun emptyStringIsNull() = assertNull(siteKey(""))
}

class TabsStoreTrackerIsolationTest {
    @Test fun eachTabKeepsItsOwnBlockedCount() {
        val store = TabsStore()
        val a = TabId("a")
        val b = TabId("b")
        store.add(a)
        store.add(b)
        store.pageChanged(a, PageState(url = "https://a.com", blockedTrackers = 9))
        store.pageChanged(b, PageState(url = "https://b.com", blockedTrackers = 2))
        store.select(a)
        assertEquals(9, store.selectedPage.value.blockedTrackers)
        store.select(b)
        assertEquals(2, store.selectedPage.value.blockedTrackers)
    }

    @Test fun aCountForAnUnknownTabChangesNothing() {
        val store = TabsStore()
        store.add(TabId("a"))
        val before = store.selectedPage.value
        store.pageChanged(TabId("ghost"), PageState(url = "https://x.com", blockedTrackers = 5))
        assertEquals(before, store.selectedPage.value)
    }
}

class PageStateStoreTrackerEdgeTest {
    private val store = PageStateStore()

    @Test fun pageStartedWhileCrashedKeepsTheOldCount() {
        store.trackerBlocked(3)
        store.crashed()
        store.pageStarted("https://a.com")
        assertEquals(3, store.state.value.blockedTrackers)
    }

    @Test fun aCountAboveTheCapIsStoredAsGiven() {
        store.trackerBlocked(1000)
        assertEquals(1000, store.state.value.blockedTrackers)
    }

    @Test fun twoPageStartsInARowEndAtZero() {
        store.pageStarted("https://a.com")
        store.trackerBlocked(4)
        store.pageStarted("https://a.com")
        store.pageStarted("https://b.com")
        assertEquals(0, store.state.value.blockedTrackers)
    }
}
