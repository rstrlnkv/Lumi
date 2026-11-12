package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveSessionsTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val live = LiveSessions()

    @Test fun evictsLeastRecentlyTouchedOverCap() {
        listOf(a, b, c).forEach(live::touch)
        assertEquals(listOf(a), live.evictions(cap = 2, keep = c))
    }

    @Test fun neverEvictsTheKeptTab() {
        listOf(a, b, c).forEach(live::touch)
        assertEquals(listOf(b), live.evictions(cap = 2, keep = a))
    }

    @Test fun touchMovesTabToMostRecent() {
        listOf(a, b, c).forEach(live::touch)
        live.touch(a)
        assertEquals(listOf(b), live.evictions(cap = 2, keep = a))
    }

    @Test fun nothingIsEvictedUnderCap() {
        listOf(a, b).forEach(live::touch)
        assertTrue(live.evictions(cap = 2, keep = b).isEmpty())
    }

    @Test fun removedTabIsNotCounted() {
        listOf(a, b, c).forEach(live::touch)
        live.remove(a)
        assertTrue(live.evictions(cap = 2, keep = c).isEmpty())
    }

    @Test fun capOfOneEvictsAllButKept() {
        listOf(a, b, c).forEach(live::touch)
        assertEquals(listOf(a, b), live.evictions(cap = 1, keep = c))
    }
}
