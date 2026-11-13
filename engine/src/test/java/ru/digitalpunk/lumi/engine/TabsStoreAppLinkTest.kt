package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TabsStoreAppLinkTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val link = TabId("link")

    private fun store(): TabsStore = TabsStore().apply {
        add(a)
        add(b)
    }

    private fun TabsStore.marked(id: TabId) = tabs.value.tabs.first { it.id == id }.openedByApp

    @Test fun linkTabGoesToTheSharedListEvenWhenANamedGroupIsCurrent() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.setCurrentGroup(g)
        store.add(link, fromApp = true)
        assertEquals(null, store.tabs.value.tabs.first { it.id == link }.groupId)
    }

    @Test fun linkTabIsSelectedAndMarked() {
        val store = store()
        store.add(link, fromApp = true)
        assertEquals(link, store.tabs.value.selectedId)
        assertTrue(store.marked(link))
        assertFalse(store.marked(a))
    }

    @Test fun linkTabGoesRightAfterTheSelectedSharedTab() {
        val store = store()
        store.select(a)
        store.add(link, fromApp = true)
        assertEquals(listOf(a, link, b), store.tabs.value.tabs.map { it.id })
    }

    @Test fun linkTabGoesAfterTheLastSharedTabWhenTheSelectedOneIsInAGroup() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.moveTab(a, g)
        store.select(a)
        store.add(link, fromApp = true)
        assertEquals(listOf(b, link), store.tabs.value.tabsIn(null).map { it.id })
    }

    @Test fun markClearsWhenThePageCanGoBack() {
        val store = store()
        store.add(link, fromApp = true)
        store.pageChanged(link, PageState(url = "https://x.test/2", canGoBack = true))
        assertFalse(store.marked(link))
        store.pageChanged(link, PageState(url = "https://x.test/1", canGoBack = false))
        assertFalse(store.marked(link))
    }

    @Test fun markStaysOnAPageChangeWithoutBackHistoryAndOnACrash() {
        val store = store()
        store.add(link, fromApp = true)
        store.pageChanged(link, PageState(url = "https://x.test/", isLoading = true))
        store.pageChanged(link, PageState(url = "https://x.test/", isCrashed = true))
        assertTrue(store.marked(link))
    }

    @Test fun closingTheMarkedSelectedTabSelectsItsOrigin() {
        val store = store()
        store.select(a)
        store.add(TabId("c"))
        store.select(a)
        store.add(link, fromApp = true)
        store.close(link)
        assertEquals(a, store.tabs.value.selectedId)
    }

    @Test fun closingSelectsTheNeighbourWhenTheOriginIsGone() {
        val store = store()
        store.select(a)
        store.add(link, fromApp = true)
        store.close(a)
        store.close(link)
        assertEquals(b, store.tabs.value.selectedId)
    }

    @Test fun aPrivateTabInFrontLocksAndIsNoOrigin() {
        val store = store()
        val p = TabId("p")
        store.add(p, private = true)
        store.add(link, fromApp = true)
        val state = store.tabs.value
        assertTrue(state.privateLocked)
        assertFalse(state.privateResume)
        assertEquals(null, state.tabs.first { it.id == link }.groupId)
        store.close(link)
        assertTrue(store.tabs.value.selectedId != p)
    }

    @Test fun restoredTabsCarryNoMark() {
        val store = TabsStore()
        store.restore(listOf(RestoredTab(a, "https://a.test/", "A")), a)
        store.restorePrivate(listOf(RestoredTab(TabId("p"), "https://p.test/", "P")))
        assertTrue(store.allTabs().none { it.openedByApp })
    }

    @Test fun twoLinkTabsKeepTheirOwnMarks() {
        val store = store()
        val second = TabId("second")
        store.add(link, fromApp = true)
        store.add(second, fromApp = true)
        store.pageChanged(link, PageState(url = "https://x.test/2", canGoBack = true))
        assertFalse(store.marked(link))
        assertTrue(store.marked(second))
        store.close(second)
        assertEquals(link, store.tabs.value.selectedId)
    }

    @Test fun closeRemovesTheMark() {
        val store = store()
        store.add(link, fromApp = true)
        store.select(a)
        store.close(link)
        assertTrue(store.allTabs().none { it.openedByApp })
        assertEquals(a, store.tabs.value.selectedId)
    }

    @Test fun aLinkIntoAnEmptyStoreHasNoOriginAndClosesAsTheLastTab() {
        val store = TabsStore()
        store.add(link, fromApp = true)
        assertTrue(store.marked(link))
        assertTrue(store.close(link))
        assertEquals(null, store.tabs.value.selectedId)
    }

    @Test fun closingAMarkedTabThatIsNotSelectedKeepsTheSelection() {
        val store = store()
        store.add(link, fromApp = true)
        store.select(a)
        store.close(link)
        assertEquals(a, store.tabs.value.selectedId)
        store.add(link, fromApp = true)
        store.select(b)
        store.close(link)
        assertEquals(b, store.tabs.value.selectedId)
    }

    @Test fun aLinkTabMovedToAGroupStillReturnsToItsOriginWhenClosed() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.add(link, fromApp = true)
        store.moveTab(link, g)
        store.close(link)
        assertEquals(b, store.tabs.value.selectedId)
    }

    @Test fun deletingTheGroupThatHoldsTheMarkedTabLeavesNoMark() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.add(link, fromApp = true)
        store.moveTab(link, g)
        store.deleteGroup(g)
        assertTrue(store.allTabs().none { it.openedByApp })
    }
}
