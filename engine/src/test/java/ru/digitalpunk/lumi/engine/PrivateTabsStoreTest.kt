package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrivateTabsStoreTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val p = TabId("p")
    private val q = TabId("q")

    /** Normal a, b; private p, q (unlocked because the first private tab opened with none hidden). */
    private fun store(): TabsStore = TabsStore().apply {
        add(a)
        add(b)
        add(p, private = true)
        add(q, private = true)
    }

    private fun TabsStore.info(id: TabId) = tabs.value.tabs.first { it.id == id }

    @Test fun newPrivateTabIsInThePrivateSetAndSelected() {
        val store = store()
        assertTrue(store.info(q).isPrivate)
        assertEquals(q, store.tabs.value.selectedId)
        assertEquals(PRIVATE_GROUP, store.tabs.value.currentGroup)
        assertEquals(2, store.tabs.value.privateCount)
    }

    @Test fun normalNewTabFromAPrivateTabLandsInTheSharedList() {
        val store = store()
        val n = TabId("n")
        store.add(n)
        assertNull(store.info(n).groupId)
        assertNull(store.tabs.value.currentGroup)
    }

    @Test fun swipeNeighbourInsidePrivateSetNeverReachesANormalTab() {
        val store = store()
        store.select(p)
        assertNull(store.neighbour(-1))
        assertEquals(q, store.neighbour(1))
    }

    @Test fun swipeNeighbourInSharedListNeverReachesAPrivateTab() {
        val store = store()
        store.select(b)
        assertNull(store.neighbour(1))
        assertEquals(a, store.neighbour(-1))
    }

    @Test fun closingTheLastNormalTabWithPrivateTabsOpenReportsLast() {
        val store = store()
        store.close(a)
        assertTrue(store.close(b))
    }

    @Test fun closingTheLastNormalTabNeverSelectsAPrivateTab() {
        val store = store()
        store.close(a)
        store.select(b)
        store.close(b)
        val selected = store.tabs.value.selectedId
        assertTrue(selected == null || !store.info(selected).isPrivate)
    }

    @Test fun closingTheLastPrivateTabSelectsANormalTab() {
        val store = store()
        store.close(p)
        assertFalse(store.close(q))
        assertEquals(b, store.tabs.value.selectedId)
    }

    @Test fun moveTabRefusesAPrivateTabAndAPrivateTarget() {
        val store = store()
        val g = store.createGroup("Work")!!
        assertFalse(store.moveTab(p, g))
        assertFalse(store.moveTab(p, null))
        assertFalse(store.moveTab(a, PRIVATE_GROUP))
        assertTrue(store.info(p).isPrivate)
        assertFalse(store.info(a).isPrivate)
    }

    @Test fun deleteGroupNeverClosesPrivateTabs() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.moveTab(a, g)
        store.deleteGroup(g)
        assertEquals(2, store.tabs.value.privateCount)
    }

    @Test fun privateGroupIsNeverANamedGroup() {
        val store = store()
        assertTrue(store.tabs.value.groups.none { it.id == PRIVATE_GROUP })
        assertFalse(store.renameGroup(PRIVATE_GROUP, "X"))
    }

    @Test fun setCurrentGroupAcceptsPrivate() {
        val store = TabsStore().apply { add(a) }
        store.setCurrentGroup(PRIVATE_GROUP)
        assertEquals(PRIVATE_GROUP, store.tabs.value.currentGroup)
    }

    @Test fun restoreDropsAStoredGroupWithThePrivateId() {
        val store = TabsStore()
        store.restore(listOf(RestoredTab(a, "https://a", "a")), a, listOf(TabGroup(PRIVATE_GROUP, "Mine")), PRIVATE_GROUP)
        assertTrue(store.tabs.value.groups.isEmpty())
        assertNull(store.tabs.value.currentGroup)
    }

    @Test fun aStoredTabWithThePrivateGroupIdGoesToTheSharedList() {
        val store = TabsStore()
        store.restore(listOf(RestoredTab(a, "https://a", "a", PRIVATE_GROUP)), a, listOf(TabGroup(PRIVATE_GROUP, "Mine")))
        assertNull(store.info(a).groupId)
        assertEquals(0, store.tabs.value.privateCount)
    }
}
