package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BlockedCounterTest {
    private val counter = BlockedCounter()

    @Test fun countsEachDistinctUriOnce() {
        assertTrue(counter.add("https://ads.example/a.js"))
        assertTrue(counter.add("https://ads.example/b.js"))
        assertEquals(2, counter.count)
    }

    @Test fun ignoresARepeatedUri() {
        counter.add("https://ads.example/a.js")
        assertFalse(counter.add("https://ads.example/a.js"))
        assertEquals(1, counter.count)
    }

    @Test fun resetReturnsTheCountToZeroAndForgetsUrisSoTheSameUriCountsAgain() {
        counter.add("https://ads.example/a.js")
        counter.reset()
        assertEquals(0, counter.count)
        assertTrue(counter.add("https://ads.example/a.js"))
        assertEquals(1, counter.count)
    }
}

class BlockedCounterEdgeTest {
    @Test fun stopsGrowingAtTheCapOf1000Uris() {
        val counter = BlockedCounter()
        repeat(1000) { assertTrue(counter.add("https://t.example/$it")) }
        assertFalse(counter.add("https://t.example/one-more"))
        assertEquals(1000, counter.count)
    }
}

class CountsAsTrackerTest {
    @Test fun aBlockingEventWithAnAntiTrackingCategoryCounts() = assertTrue(countsAsTracker(antiTracking = 2, cookie = 0, blocking = true))

    @Test fun aBlockingEventWithOnlyACookieCategoryCounts() = assertTrue(countsAsTracker(antiTracking = 0, cookie = 4, blocking = true))

    @Test fun aSafeBrowsingOnlyEventDoesNotCount() = assertFalse(countsAsTracker(antiTracking = 0, cookie = 0, blocking = true))

    @Test fun aNonBlockingEventDoesNotCount() = assertFalse(countsAsTracker(antiTracking = 2, cookie = 0, blocking = false))

    @Test fun anEventWithNoCategoriesDoesNotCount() = assertFalse(countsAsTracker(antiTracking = 0, cookie = 0, blocking = true))
}
