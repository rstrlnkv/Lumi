package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TabsStoreDropNormalTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val p = TabId("p")

    @Test fun `drops normal tabs and groups, keeps private tabs`() {
        val store = TabsStore().apply {
            add(a)
            add(b)
            add(p, private = true)
        }
        val g = store.createGroup("Work")!!
        store.moveTab(b, g)
        store.dropNormal()
        val state = store.tabs.value
        assertTrue(state.groups.isEmpty())
        assertEquals(1, state.privateCount)
        assertTrue(state.tabs.none { !it.isPrivate })
    }

    @Test fun `returns the dropped ids`() {
        val store = TabsStore().apply {
            add(a)
            add(b)
            add(c)
        }
        val g = store.createGroup("Work")!!
        store.moveTab(c, g)
        assertEquals(setOf(a, b, c), store.dropNormal().toSet())
    }

    @Test fun `with no private tab nothing is left`() {
        val store = TabsStore().apply {
            add(a)
            add(b)
        }
        store.dropNormal()
        assertTrue(store.tabs.value.tabs.isEmpty())
        assertTrue(store.allTabs().isEmpty())
    }
}
