package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class PageStateStoreTrackerTest {
    private val store = PageStateStore()
    private val state get() = store.state.value

    @Test fun pageStartedResetsTheBlockedCountToZero() {
        store.trackerBlocked(5)
        store.pageStarted("https://a.com")
        assertEquals(0, state.blockedTrackers)
    }

    @Test fun trackerBlockedSetsTheCount() {
        store.trackerBlocked(3)
        assertEquals(3, state.blockedTrackers)
    }

    @Test fun trackerBlockedIsIgnoredWhileCrashed() {
        store.trackerBlocked(2)
        store.crashed()
        store.trackerBlocked(7)
        assertEquals(2, state.blockedTrackers)
    }

    @Test fun locationChangedForAFragmentKeepsTheCount() {
        store.pageStarted("https://a.com/")
        store.trackerBlocked(4)
        store.locationChanged("https://a.com/#top")
        assertEquals(4, state.blockedTrackers)
    }

    @Test fun locationChangedToAnotherSiteWithoutAPageStartResetsTheCount() {
        store.pageStarted("https://a.com/")
        store.trackerBlocked(4)
        store.locationChanged("https://b.com/")
        assertEquals(0, state.blockedTrackers)
    }

    @Test fun locationChangedWithinTheSameSiteKeepsTheCount() {
        store.pageStarted("https://a.com/one")
        store.trackerBlocked(4)
        store.locationChanged("https://www.a.com/two")
        assertEquals(4, state.blockedTrackers)
    }
}

class TabsStoreTrackerTest {
    @Test fun aCountChangeInABackgroundTabDoesNotChangeTheSelectedPage() {
        val store = TabsStore()
        val a = TabId("a")
        store.add(a)
        store.add(TabId("b"))
        val before = store.selectedPage.value
        store.pageChanged(a, PageState(url = "https://a.com", blockedTrackers = 9))
        assertEquals(before, store.selectedPage.value)
    }
}
