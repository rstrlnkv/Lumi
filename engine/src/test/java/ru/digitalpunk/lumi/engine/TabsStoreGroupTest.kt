package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabsStoreGroupTest {
    private val a = TabId("a")
    private val b = TabId("b")

    private fun store(): TabsStore = TabsStore().apply {
        add(a)
        add(b)
    }

    @Test fun selectingATabMakesItsGroupCurrent() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.moveTab(a, g)
        store.select(a)
        assertEquals(g, store.tabs.value.currentGroup)
        store.select(b)
        assertNull(store.tabs.value.currentGroup)
    }

    @Test fun showSelectedGroupMovesCurrentToTheSelectedTabsGroup() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.moveTab(b, g)
        store.setCurrentGroup(null)
        store.showSelectedGroup()
        assertEquals(g, store.tabs.value.currentGroup)
    }

    @Test fun chipTapChangesOnlyTheCurrentGroup() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.setCurrentGroup(g)
        assertEquals(g, store.tabs.value.currentGroup)
        assertEquals(b, store.tabs.value.selectedId)
    }

    @Test fun newTabOpensInTheCurrentGroup() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.moveTab(a, g)
        store.setCurrentGroup(g)
        store.add(TabId("n"))
        assertEquals(g, store.tabs.value.tabs.first { it.id == TabId("n") }.groupId)
    }

    @Test fun deleteGroupResetsCurrentToTheSharedList() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.moveTab(a, g)
        store.setCurrentGroup(g)
        assertEquals(false, store.deleteGroup(g))
        assertNull(store.tabs.value.currentGroup)
        assertEquals(listOf(b), store.tabs.value.tabs.map { it.id })
    }

    @Test fun publishedStateCarriesEachTabsGroupAndTheGroupList() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.moveTab(a, g)
        val state = store.tabs.value
        assertEquals(listOf(TabGroup(g, "Work")), state.groups)
        assertEquals(listOf(g, null), state.tabs.map { it.groupId })
    }

    @Test fun moveTabToAnUnknownGroupIsRefused() {
        val store = store()
        assertEquals(false, store.moveTab(a, GroupId("nope")))
        assertTrue(store.tabs.value.tabs.all { it.groupId == null })
    }

    @Test fun closeInsideAGroupKeepsTheGroupEvenWhenEmpty() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.moveTab(a, g)
        store.close(a)
        assertEquals(listOf(g), store.tabs.value.groups.map { it.id })
    }
}
