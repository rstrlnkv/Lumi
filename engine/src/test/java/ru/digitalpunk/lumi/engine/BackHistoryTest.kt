package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackHistoryTest {
    private val items = listOf(
        HistoryEntry("One", "https://a.com/1"),
        HistoryEntry("Two", "https://a.com/2"),
        HistoryEntry("Three", "https://a.com/3"),
        HistoryEntry("Four", "https://a.com/4"),
    )

    @Test fun currentPageIsLastAndBackEntriesComeBeforeIt() {
        val rows = backHistory(items.take(3), currentIndex = 2).rows
        assertEquals(listOf("One", "Two", "Three"), rows.map { it.title })
        assertEquals(listOf(false, false, true), rows.map { it.isCurrent })
    }

    @Test fun forwardEntriesAreDropped() {
        val rows = backHistory(items, currentIndex = 1).rows
        assertEquals(listOf("https://a.com/1", "https://a.com/2"), rows.map { it.url })
        assertTrue(rows.last().isCurrent)
    }

    @Test fun rowsKeepTheSessionIndexOfEachEntry() {
        assertEquals(listOf(0, 1, 2), backHistory(items, currentIndex = 2).rows.map { it.index })
    }

    @Test fun emptyListOrIndexOutOfRangeGivesNoRows() {
        assertEquals(BackHistory(), backHistory(emptyList(), 0))
        assertEquals(BackHistory(), backHistory(items, -1))
        assertEquals(BackHistory(), backHistory(items, items.size))
    }

    @Test fun singleEntryHasNoBack() {
        val history = backHistory(items.take(1), 0)
        assertEquals(1, history.rows.size)
        assertFalse(history.hasBack)
        assertFalse(BackHistory().hasBack)
        assertTrue(backHistory(items.take(2), 1).hasBack)
    }

    @Test fun nullTitleBecomesEmpty() {
        val rows = backHistory(listOf(HistoryEntry(null, "https://a.com/")), 0).rows
        assertEquals("", rows.single().title)
    }

    @Test fun canGoToAcceptsOnlyABackIndex() {
        val history = backHistory(items, currentIndex = 2)
        assertTrue(history.canGoTo(0))
        assertTrue(history.canGoTo(1))
        listOf(2, 3, -1, 4, 99).forEach { assertFalse(history.canGoTo(it), "index $it") }
    }

    @Test fun visibleRowsIsEightAtMost() {
        assertEquals(listOf(0, 1, 8, 8, 8), listOf(0, 1, 8, 9, 50).map(::visibleHistoryRows))
    }
}
