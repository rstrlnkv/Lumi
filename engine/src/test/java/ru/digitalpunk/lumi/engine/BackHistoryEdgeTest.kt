package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackHistoryEdgeTest {
    @Test fun hugeHistoryKeepsEveryBackRowAndTheCurrentLast() {
        val items = List(200_000) { HistoryEntry("t$it", "https://a.com/$it") }
        val h = backHistory(items, items.lastIndex)
        assertEquals(200_000, h.rows.size)
        assertTrue(h.rows.last().isCurrent)
        assertEquals(1, h.rows.count { it.isCurrent })
        assertTrue(h.canGoTo(199_998))
        assertFalse(h.canGoTo(199_999))
        assertEquals(8, visibleHistoryRows(h.rows.size))
    }

    @Test fun sameUrlTwiceStaysTwoRowsWithTheirOwnIndexes() {
        val h = backHistory(listOf(HistoryEntry("A", "https://a/"), HistoryEntry("A", "https://a/"), HistoryEntry("A", "https://a/")), 2)
        assertEquals(listOf(0, 1, 2), h.rows.map { it.index })
        assertEquals(listOf(false, false, true), h.rows.map { it.isCurrent })
    }

    @Test fun negativeAndTooLargeIndexGiveNoMenu() {
        val items = listOf(HistoryEntry("A", "https://a/"), HistoryEntry("B", "https://b/"))
        assertFalse(backHistory(items, -1).hasBack)
        assertFalse(backHistory(items, 2).hasBack)
        assertFalse(backHistory(items, Int.MAX_VALUE).hasBack)
        assertFalse(backHistory(items, Int.MIN_VALUE).hasBack)
        assertFalse(backHistory(emptyList(), 0).hasBack)
    }

    @Test fun anIndexNotInTheListIsRefusedAtAnyValue() {
        val h = backHistory(listOf(HistoryEntry("A", "https://a/"), HistoryEntry("B", "https://b/")), 1)
        listOf(-1, 1, 2, Int.MAX_VALUE, Int.MIN_VALUE).forEach { assertFalse(h.canGoTo(it), "index $it") }
        assertTrue(h.canGoTo(0))
    }

    @Test fun anEmptyOrOutOfRangeUpdateReplacesAStaleHistory() {
        val store = PageStateStore()
        val items = listOf(HistoryEntry("A", "https://a/"), HistoryEntry("B", "https://b/"))
        store.historyChanged(items, 1)
        assertTrue(store.state.value.history.hasBack)
        store.historyChanged(emptyList(), -1)
        assertEquals(BackHistory(), store.state.value.history)
        store.historyChanged(items, 1)
        store.historyChanged(items, 5)
        assertEquals(BackHistory(), store.state.value.history)
    }

    @Test fun historySurvivesPageEventsAndDoesNotTouchOtherFields() {
        val store = PageStateStore()
        val items = listOf(HistoryEntry("A", "https://a/"), HistoryEntry("B", "https://b/"))
        store.historyChanged(items, 1)
        val h = store.state.value.history
        store.pageStarted("https://b/")
        store.progressChanged(40)
        store.pageStopped()
        assertEquals(h, store.state.value.history)
        val before = store.state.value
        store.historyChanged(items, 0)
        assertEquals(before.copy(history = store.state.value.history), store.state.value)
    }
}
