package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabsStoreEdgeTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val store = TabsStore()

    @Test fun pageChangeOfAnUnknownTabIsIgnored() {
        store.add(a)
        store.pageChanged(TabId("ghost"), PageState(url = "https://x.com"))
        assertEquals(listOf(a), store.tabs.value.tabs.map { it.id })
        assertEquals("about:blank", store.selectedPage.value.url)
    }

    @Test fun lateEventForAClosedTabDoesNotResurrectIt() {
        store.add(a)
        store.add(b)
        store.close(b)
        store.pageChanged(b, PageState(url = "https://b.com"))
        store.titleChanged(b, "B")
        assertEquals(listOf(a), store.tabs.value.tabs.map { it.id })
    }

    @Test fun closingTheLastTabLeavesAnEmptyStateAndDefaultPage() {
        store.add(a)
        store.pageChanged(a, PageState(url = "https://a.com"))
        assertTrue(store.close(a))
        assertTrue(store.tabs.value.tabs.isEmpty())
        assertNull(store.tabs.value.selectedId)
        assertEquals(PageState(), store.selectedPage.value)
        assertFalse(store.close(a))
    }

    @Test fun restoreOfNothingGivesEmptyState() {
        store.restore(emptyList(), selected = a)
        assertTrue(store.tabs.value.tabs.isEmpty())
        assertNull(store.tabs.value.selectedId)
    }
}
