package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PrivateLockStoreTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val p = TabId("p")

    private fun store(): TabsStore = TabsStore().apply {
        add(a)
        add(b)
        select(a)
        add(p, private = true)
    }

    @Test fun lockMovesSelectionOffPrivateAndRemembersResume() {
        val store = store()
        store.lock()
        assertEquals(a, store.tabs.value.selectedId)
        assertTrue(store.tabs.value.privateResume)
        assertTrue(store.tabs.value.privateLocked)
    }

    @Test fun lockedStateHidesPrivateTabsFromTheList() {
        val store = store()
        store.lock()
        assertTrue(store.tabs.value.tabs.none { it.isPrivate })
        assertEquals(listOf(a, b), store.tabs.value.tabs.map { it.id })
    }

    @Test fun lockedStateStillReportsPrivateCount() {
        val store = store()
        store.lock()
        assertEquals(1, store.tabs.value.privateCount)
    }

    @Test fun unlockRestoresThePrivateSelectionAndGroup() {
        val store = store()
        store.lock()
        store.unlock()
        assertEquals(p, store.tabs.value.selectedId)
        assertEquals(PRIVATE_GROUP, store.tabs.value.currentGroup)
        assertFalse(store.tabs.value.privateLocked)
        assertFalse(store.tabs.value.privateResume)
    }

    @Test fun selectingANormalTabWhileLockedClearsResume() {
        val store = store()
        store.lock()
        store.select(b)
        assertFalse(store.tabs.value.privateResume)
        store.unlock()
        assertEquals(b, store.tabs.value.selectedId)
    }

    @Test fun selectingAPrivateTabWhileLockedIsIgnored() {
        val store = store()
        store.lock()
        store.select(p)
        assertEquals(a, store.tabs.value.selectedId)
    }

    @Test fun firstPrivateTabWhileLockedWithNoPrivateTabsDoesNotLock() {
        val store = TabsStore().apply { add(a) }
        assertFalse(store.tabs.value.privateLocked)
        store.add(p, private = true)
        assertFalse(store.tabs.value.privateLocked)
        assertEquals(p, store.tabs.value.selectedId)
    }

    @Test fun coldStartRestoredPrivateTabsAreLocked() {
        val store = TabsStore()
        store.restore(listOf(RestoredTab(a, "https://a", "a")), a)
        store.restorePrivate(listOf(RestoredTab(p, "https://p", "p")))
        assertTrue(store.tabs.value.privateLocked)
        assertEquals(1, store.tabs.value.privateCount)
        assertEquals(a, store.tabs.value.selectedId)
        assertTrue(store.tabs.value.tabs.none { it.isPrivate })
    }

    @Test fun lockWithNoPrivateTabsChangesNothing() {
        val store = TabsStore().apply { add(a) }
        val before = store.tabs.value
        store.lock()
        assertEquals(before, store.tabs.value)
    }
}
