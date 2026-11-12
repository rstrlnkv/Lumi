package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestoreGroupsTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val w = GroupId("w")
    private val r = GroupId("r")

    private fun tab(id: TabId, group: GroupId?) = RestoredTab(id, "https://x", "t", group)

    @Test fun tabWhoseGroupIsGoneFallsBackToTheSharedListAndKeepsItsPlace() {
        val store = TabsStore()
        store.restore(listOf(tab(a, w), tab(b, GroupId("gone")), tab(c, null)), a, listOf(TabGroup(w, "Work")), null)
        val tabs = store.tabs.value.tabs
        assertEquals(listOf(a, b, c), tabs.map { it.id })
        assertEquals(listOf(w, null, null), tabs.map { it.groupId })
    }

    @Test fun groupWithBlankNameOrRepeatedIdIsDropped() {
        val store = TabsStore()
        store.restore(
            listOf(tab(a, w), tab(b, r)),
            a,
            listOf(TabGroup(w, "Work"), TabGroup(w, "Again"), TabGroup(r, "  ")),
            null,
        )
        assertEquals(listOf(TabGroup(w, "Work")), store.tabs.value.groups)
        assertNull(store.tabs.value.tabs[1].groupId)
    }

    @Test fun currentGroupThatIsGoneFallsBackToTheSelectedTabsGroup() {
        val store = TabsStore()
        store.restore(listOf(tab(a, w), tab(b, null)), a, listOf(TabGroup(w, "Work")), GroupId("gone"))
        assertEquals(w, store.tabs.value.currentGroup)
    }

    @Test fun storedSharedCurrentGroupStaysShared() {
        val store = TabsStore()
        store.restore(listOf(tab(a, w), tab(b, null)), a, listOf(TabGroup(w, "Work")), null)
        assertNull(store.tabs.value.currentGroup)
    }

    @Test fun restoredGroupsKeepTheStoredOrder() {
        val store = TabsStore()
        store.restore(listOf(tab(a, null)), a, listOf(TabGroup(r, "Reading"), TabGroup(w, "Work")), r)
        assertEquals(listOf(r, w), store.tabs.value.groups.map { it.id })
        assertEquals(r, store.tabs.value.currentGroup)
    }

    @Test fun restoreWithoutGroupsBehavesAsInM3() {
        val store = TabsStore()
        store.restore(listOf(tab(a, null), tab(b, null)), b)
        assertEquals(b, store.tabs.value.selectedId)
        assertEquals(emptyList(), store.tabs.value.groups)
        assertNull(store.tabs.value.currentGroup)
    }
}
