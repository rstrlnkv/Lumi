package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The private tabs lock as soon as the person leaves the private set inside the app; coming back needs an unlock. */
class PrivateLeaveLockTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val p = TabId("p")
    private val q = TabId("q")

    /** Normal tabs a and b, a selected last; then the private tab p, unlocked and in front. */
    private fun store(): TabsStore = TabsStore().apply {
        add(a)
        add(b)
        select(a)
        add(p, private = true)
    }

    private fun assertLockedWithoutResume(store: TabsStore) {
        assertTrue(store.tabs.value.privateLocked)
        assertFalse(store.tabs.value.privateResume)
        assertTrue(store.tabs.value.tabs.none { it.isPrivate })
    }

    @Test fun selectingANormalTabFromAPrivateTabLocksThePrivateTabs() {
        val store = store()
        store.select(b)
        assertEquals(b, store.tabs.value.selectedId)
        assertLockedWithoutResume(store)
    }

    @Test fun aPrivateTabCannotBeSelectedAgainAfterLeavingUntilUnlock() {
        val store = store()
        store.select(b)
        store.select(p)
        assertEquals(b, store.tabs.value.selectedId)
        store.unlock()
        store.select(p)
        assertEquals(p, store.tabs.value.selectedId)
        assertFalse(store.tabs.value.privateLocked)
    }

    @Test fun openingANormalTabFromAPrivateTabLocksThePrivateTabs() {
        val store = store()
        store.add(c)
        assertEquals(c, store.tabs.value.selectedId)
        assertLockedWithoutResume(store)
    }

    @Test fun switchingTheChipAwayFromPrivateLocksAndPutsTheLastNormalTabInFront() {
        val store = store()
        store.setCurrentGroup(null)
        assertEquals(a, store.tabs.value.selectedId)
        assertNull(store.tabs.value.currentGroup)
        assertLockedWithoutResume(store)
    }

    @Test fun movingBetweenPrivateTabsKeepsThemUnlocked() {
        val store = store()
        store.add(q, private = true)
        store.select(p)
        store.close(p)
        assertEquals(q, store.tabs.value.selectedId)
        assertFalse(store.tabs.value.privateLocked)
    }

    @Test fun theGridOnThePrivateChipCountsAsInViewUntilItCloses() {
        val store = store()
        store.lock()
        store.leavePrivate()
        store.setCurrentGroup(PRIVATE_GROUP)
        store.unlock()
        assertEquals(a, store.tabs.value.selectedId)
        store.titleChanged(p, "still here")
        assertFalse(store.tabs.value.privateLocked)
        store.showSelectedGroup()
        assertLockedWithoutResume(store)
    }

    @Test fun leavingBackgroundLockStillResumesThePrivateTabOnUnlock() {
        val store = store()
        store.lock()
        assertTrue(store.tabs.value.privateResume)
        store.unlock()
        assertEquals(p, store.tabs.value.selectedId)
        assertFalse(store.tabs.value.privateLocked)
    }
}
