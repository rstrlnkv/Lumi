package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "Open in new tab" from a link: the tab opens behind the current one. */
class BackgroundTabTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val n1 = TabId("n1")
    private val n2 = TabId("n2")
    private val n3 = TabId("n3")
    private val p = TabId("p")
    private val url = "https://news.example/story"

    private val TabsStore.order get() = tabs.value.tabs.map { it.id }
    private val TabsStore.selected get() = tabs.value.selectedId
    private val TabsStore.current get() = tabs.value.currentGroup
    private fun TabsStore.info(id: TabId) = tabs.value.tabs.first { it.id == id }

    /** a, b, c in the shared list; a selected. */
    private fun store() = TabsStore().apply {
        add(a); add(b); add(c)
        select(a)
    }

    @Test fun backgroundTabGoesRightAfterTheCurrentTabInItsGroup() {
        val store = store()
        store.add(n1, select = false, url = url)
        assertEquals(listOf(a, n1, b, c), store.order)
        assertEquals(a, store.selected)
        assertEquals(null, store.current)
        assertEquals(0, store.tabs.value.privateCount)
    }

    @Test fun backgroundTabKeepsSelectionAndCurrentGroup() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.moveTab(a, g)
        store.select(a)
        store.add(n1, select = false, url = url)
        assertEquals(a, store.selected)
        assertEquals(g, store.current)
        assertEquals(g, store.info(n1).groupId)
        assertEquals(listOf(a, n1, b, c), store.order)
        assertEquals(PageState(), store.selectedPage.value)
    }

    @Test fun backgroundTabCarriesItsUrlAndAnEmptyTitle() {
        val store = store()
        store.add(n1, select = false, url = url)
        assertEquals(url, store.info(n1).url)
        assertEquals("", store.info(n1).title)
        assertFalse(store.info(n1).isLoading)
    }

    @Test fun secondBackgroundTabFromTheSameTabGoesAfterTheFirst() {
        val store = store()
        store.add(n1, select = false, url = url)
        store.add(n2, select = false, url = url)
        store.add(n3, select = false, url = url)
        assertEquals(listOf(a, n1, n2, n3, b, c), store.order)
        assertEquals(a, store.selected)
    }

    @Test fun chainRestartsWhenAnotherTabIsSelectedFirst() {
        val store = store()
        store.add(n1, select = false, url = url)
        store.select(b)
        store.add(n2, select = false, url = url)
        assertEquals(listOf(a, n1, b, n2, c), store.order)
        store.select(a)
        store.add(n3, select = false, url = url)
        assertEquals(listOf(a, n3, n1, b, n2, c), store.order)
    }

    @Test fun selectingAddEndsTheChain() {
        val store = store()
        store.add(n1, select = false, url = url)
        store.add(n2)
        store.select(a)
        store.add(n3, select = false, url = url)
        assertEquals(listOf(a, n3, n2, n1, b, c), store.order)
    }

    @Test fun chainFallsBackToTheCurrentTabWhenTheLastChildWasClosed() {
        val store = store()
        store.add(n1, select = false, url = url)
        store.close(n1)
        store.add(n2, select = false, url = url)
        assertEquals(listOf(a, n2, b, c), store.order)
        assertEquals(a, store.selected)
    }

    @Test fun chainFallsBackWhenTheLastChildMovedToAnotherGroup() {
        val store = store()
        val g = store.createGroup("Later")!!
        store.add(n1, select = false, url = url)
        store.moveTab(n1, g)
        store.add(n2, select = false, url = url)
        assertEquals(listOf(a, n2, n1, b, c), store.order)
        assertEquals(null, store.info(n2).groupId)
    }

    @Test fun backgroundPrivateTabFromANormalTabJoinsThePrivateSetAndLeavesSelectionAlone() {
        val store = store()
        store.add(p, private = true, select = false, url = url)
        assertEquals(a, store.selected)
        assertEquals(null, store.current)
        assertEquals(1, store.tabs.value.privateCount)
        assertEquals(PRIVATE_GROUP, store.info(p).groupId)
        assertTrue(store.info(p).isPrivate)
        assertEquals(listOf(a, p, b, c), store.order)
    }

    @Test fun firstBackgroundPrivateTabUnlocksTheSetLikeAnyFirstPrivateTab() {
        val store = store()
        store.add(p, private = true, select = false, url = url)
        assertFalse(store.tabs.value.privateLocked)
        assertTrue(p in store.order)
    }

    @Test fun backgroundPrivateTabFromAPrivateTabGoesAfterIt() {
        val store = store()
        val q = TabId("q")
        store.add(p, private = true)
        store.add(q, private = true)
        store.select(p)
        store.add(n1, private = true, select = false, url = url)
        assertEquals(listOf(a, p, n1, q, b, c), store.order)
        assertEquals(p, store.selected)
        assertEquals(PRIVATE_GROUP, store.current)
        assertEquals(3, store.tabs.value.privateCount)
    }

    @Test fun backgroundNormalTabFromAPrivateTabLandsInTheSharedList() {
        val store = store()
        store.add(p, private = true)
        store.add(n1, select = false, url = url)
        assertEquals(null, store.info(n1).groupId)
        assertEquals(p, store.selected)
        assertEquals(PRIVATE_GROUP, store.current)
        assertFalse(store.tabs.value.privateLocked)
        assertEquals(listOf(a, p, b, c, n1), store.order)
    }

    @Test fun backgroundTabDoesNotDropAPendingPrivateResume() {
        val store = store()
        store.add(p, private = true)
        store.lock()
        assertTrue(store.tabs.value.privateResume)
        store.add(n1, select = false, url = url)
        assertTrue(store.tabs.value.privateResume)
        store.unlock()
        assertEquals(p, store.selected)
    }

    @Test fun backgroundAddWithNothingSelectedAppends() {
        val store = TabsStore()
        store.add(n1, select = false, url = url)
        assertEquals(listOf(n1), store.order)
        assertEquals(null, store.selected)
    }
}
