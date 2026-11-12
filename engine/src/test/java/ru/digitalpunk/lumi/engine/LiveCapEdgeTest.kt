package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveCapEdgeTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val live = LiveSessions().apply { listOf(a, b, c).forEach(::touch) }

    @Test fun capOfZeroEvictsEveryoneButTheKeptTab() {
        assertEquals(listOf(a, b), live.evictions(cap = 0, keep = c))
    }

    @Test fun negativeCapNeverNamesTheKeptTabOrMoreThanExist() {
        val out = live.evictions(cap = -3, keep = b)
        assertEquals(listOf(a, c), out)
    }

    @Test fun keptTabNotTrackedYetIsNotNamedAndCountsNothing() {
        assertEquals(listOf(a), live.evictions(cap = 2, keep = TabId("new")))
    }

    @Test fun emptyTrackerEvictsNothing() {
        assertTrue(LiveSessions().evictions(cap = 0, keep = a).isEmpty())
    }

    @Test fun touchTwiceDoesNotCountTwice() {
        live.touch(a)
        live.touch(a)
        assertTrue(live.evictions(cap = 3, keep = a).isEmpty())
    }

    @Test fun zeroRamAndZeroClassGiveTheMinimumOfTwo() {
        assertEquals(2, liveSessionCap(totalRamMb = 0, memoryClassMb = 0, isLowRam = false))
    }

    @Test fun negativeInputsStillGiveTheMinimumOfTwo() {
        assertEquals(2, liveSessionCap(totalRamMb = -1, memoryClassMb = -1, isLowRam = false))
    }

    @Test fun capBoundariesAreTwoAndSix() {
        // 768 MB per tab by RAM, 48 MB per tab by heap; the smaller wins.
        assertEquals(2, liveSessionCap(1536, 512, false))
        assertEquals(2, liveSessionCap(2303, 512, false))
        assertEquals(3, liveSessionCap(2304, 512, false))
        assertEquals(5, liveSessionCap(4607, 512, false))
        assertEquals(6, liveSessionCap(4608, 512, false))
        assertEquals(6, liveSessionCap(5376, 512, false))
        assertEquals(3, liveSessionCap(16000, 144, false))
        assertEquals(2, liveSessionCap(16000, 95, false))
    }

    @Test fun trimBoundariesAreTenAndFifteen() {
        assertEquals(6, capForTrim(6, 9))
        assertEquals(3, capForTrim(6, 10))
        assertEquals(3, capForTrim(6, 14))
        assertEquals(1, capForTrim(6, 15))
    }

    @Test fun unknownTrimLevelsBelowTenLeaveTheCap() {
        listOf(0, 5, 9, -1).forEach { assertEquals(4, capForTrim(4, it), "level $it") }
    }

    @Test fun uiHiddenDoesNotEvictBecauseItFiresOnEveryBackgrounding() {
        assertEquals(4, capForTrim(4, 20))
    }

    @Test fun backgroundHalvesTheCapAndModerateAndCompleteKeepOnlyTheSelectedTab() {
        assertEquals(2, capForTrim(4, 40))
        assertEquals(1, capForTrim(1, 40))
        listOf(60, 80, 1000).forEach { assertEquals(1, capForTrim(4, it), "level $it") }
    }

    @Test fun levelsBetweenTheKnownOnesFollowTheLevelBelow() {
        listOf(16, 19).forEach { assertEquals(1, capForTrim(4, it), "level $it") }
        listOf(21, 39).forEach { assertEquals(4, capForTrim(4, it), "level $it") }
        listOf(41, 59).forEach { assertEquals(2, capForTrim(4, it), "level $it") }
    }
}
