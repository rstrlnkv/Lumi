package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabsStoreTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val store = TabsStore()

    private fun twoTabs() {
        store.add(a)
        store.add(b)
    }

    @Test fun startsEmpty() {
        assertTrue(store.tabs.value.tabs.isEmpty())
        assertEquals(null, store.tabs.value.selectedId)
    }

    @Test fun selectedPageFollowsTheSelectedTab() {
        twoTabs()
        store.pageChanged(a, PageState(url = "https://a.com"))
        store.pageChanged(b, PageState(url = "https://b.com"))
        assertEquals("https://b.com", store.selectedPage.value.url)
        store.select(a)
        assertEquals("https://a.com", store.selectedPage.value.url)
    }

    @Test fun backgroundPageChangeDoesNotTouchSelectedPage() {
        twoTabs()
        val before = store.selectedPage.value
        store.pageChanged(a, PageState(url = "https://a.com", isLoading = true))
        assertEquals(before, store.selectedPage.value)
        assertTrue(store.tabs.value.tabs.first { it.id == a }.isLoading)
    }

    @Test fun titleChangeUpdatesTheList() {
        twoTabs()
        store.titleChanged(a, "Alpha")
        assertEquals("Alpha", store.tabs.value.tabs.first { it.id == a }.title)
    }

    @Test fun crashOnOneTabDoesNotMarkOthers() {
        twoTabs()
        store.pageChanged(a, PageState(isCrashed = true))
        assertTrue(store.tabs.value.tabs.first { it.id == a }.isCrashed)
        assertFalse(store.tabs.value.tabs.first { it.id == b }.isCrashed)
        assertFalse(store.selectedPage.value.isCrashed)
    }

    @Test fun closingTheSelectedTabMovesSelectedPage() {
        twoTabs()
        store.pageChanged(a, PageState(url = "https://a.com"))
        store.close(b)
        assertEquals(a, store.tabs.value.selectedId)
        assertEquals("https://a.com", store.selectedPage.value.url)
    }

    @Test fun restoreKeepsOrderSelectionAndUrls() {
        store.restore(listOf(RestoredTab(a, "https://a.com", "A"), RestoredTab(b, "https://b.com", "B")), selected = a)
        assertEquals(listOf(a, b), store.tabs.value.tabs.map { it.id })
        assertEquals(a, store.tabs.value.selectedId)
        assertEquals("https://a.com", store.selectedPage.value.url)
        assertEquals("B", store.tabs.value.tabs[1].title)
    }

    @Test fun restoreFallsBackToFirstTabWhenSelectionIsUnknown() {
        store.restore(listOf(RestoredTab(a, "https://a.com", "A")), selected = TabId("gone"))
        assertEquals(a, store.tabs.value.selectedId)
    }
}
