package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals

class PageStateStoreHistoryTest {
    private val store = PageStateStore()
    private val items = listOf(HistoryEntry("A", "https://a.com/"), HistoryEntry("B", "https://b.com/"))

    @Test fun historyChangedStoresTheRows() {
        store.historyChanged(items, 1)
        assertEquals(backHistory(items, 1), store.state.value.history)
    }

    @Test fun crashClearsTheHistory() {
        store.historyChanged(items, 1)
        store.crashed()
        assertEquals(BackHistory(), store.state.value.history)
    }

    @Test fun historyEventsAreIgnoredWhileCrashed() {
        store.crashed()
        store.historyChanged(items, 1)
        assertEquals(BackHistory(), store.state.value.history)
        store.recovering()
        store.historyChanged(items, 1)
        assertEquals(backHistory(items, 1), store.state.value.history)
    }
}
