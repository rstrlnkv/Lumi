package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inputs around the lock-on-leave rule: swipe, chip, new tab, last private tab, re-entry, and background after unlock. */
class PrivateLeaveLockEdgeTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val p = TabId("p")
    private val q = TabId("q")

    /** Normal a, b (a selected last), then private p and q; q is in front, unlocked. */
    private fun store(): TabsStore = TabsStore().apply {
        add(a)
        add(b)
        select(a)
        add(p, private = true)
        add(q, private = true)
    }

    private fun TabsStore.assertHidden() {
        assertTrue(tabs.value.privateLocked)
        assertFalse(tabs.value.privateResume)
        assertTrue(tabs.value.tabs.none { it.isPrivate })
        assertTrue(tabs.value.tabsIn(PRIVATE_GROUP).isEmpty())
    }

    @Test fun swipingBetweenPrivateTabsStaysUnlockedAndSwipeFromTheEndDoesNothing() {
        val store = store()
        store.select(p)
        val next = store.neighbour(1)
        assertEquals(q, next)
        store.select(next!!)
        assertNull(store.neighbour(1))
        assertFalse(store.tabs.value.privateLocked)
        assertEquals(PRIVATE_GROUP, store.tabs.value.currentGroup)
    }

    @Test fun swipingAmongNormalTabsAfterLeavingKeepsPrivateLockedWithoutResume() {
        val store = store()
        store.select(a)
        store.select(store.neighbour(1)!!)
        assertEquals(b, store.tabs.value.selectedId)
        store.assertHidden()
    }

    @Test fun switchingToANamedGroupChipLocksAndKeepsTheLastNormalTabInFront() {
        val store = store()
        val g = store.createGroup("Work")!!
        store.setCurrentGroup(g)
        assertEquals(g, store.tabs.value.currentGroup)
        assertEquals(a, store.tabs.value.selectedId)
        store.assertHidden()
    }

    @Test fun switchingToTheSharedChipLocksPrivateTabs() {
        val store = store()
        store.setCurrentGroup(null)
        store.assertHidden()
        assertEquals(2, store.tabs.value.privateCount)
    }

    @Test fun openingANormalTabWhileTheGridShowsThePrivateChipLocks() {
        val store = store()
        store.setCurrentGroup(PRIVATE_GROUP)
        store.add(c)
        assertEquals(c, store.tabs.value.selectedId)
        store.assertHidden()
    }

    @Test fun closingTheLastPrivateTabLeavesNothingToResumeAndAFreshPrivateTabOpensUnlocked() {
        val store = store()
        store.close(q)
        assertFalse(store.tabs.value.privateLocked)
        assertEquals(p, store.tabs.value.selectedId)
        store.close(p)
        assertEquals(0, store.tabs.value.privateCount)
        assertFalse(store.tabs.value.privateResume)
        assertTrue(store.tabs.value.tabs.none { it.isPrivate })
        store.add(c)
        assertEquals(c, store.tabs.value.selectedId)
        store.add(TabId("fresh"), private = true)
        assertFalse(store.tabs.value.privateLocked)
        assertEquals(TabId("fresh"), store.tabs.value.selectedId)
        assertEquals(1, store.tabs.value.privateCount)
    }

    @Test fun closingASelectedPrivateTabFallingBackToNormalLeavesTheOtherPrivateTabsUsable() {
        val store = store()
        store.close(q)
        store.close(p)
        store.add(TabId("r"), private = true)
        store.select(a)
        store.assertHidden()
        assertEquals(1, store.tabs.value.privateCount)
    }

    @Test fun returningToThePrivateChipWhileLockedStaysLockedAndSelectsNoPrivateTab() {
        val store = store()
        store.select(a)
        store.setCurrentGroup(PRIVATE_GROUP)
        assertEquals(PRIVATE_GROUP, store.tabs.value.currentGroup)
        assertEquals(a, store.tabs.value.selectedId)
        store.assertHidden()
        store.select(q)
        assertEquals(a, store.tabs.value.selectedId)
        store.assertHidden()
    }

    @Test fun leavingAgainAfterReturningLockedLeavesTheStateLockedAndUnresumable() {
        val store = store()
        store.select(a)
        store.setCurrentGroup(PRIVATE_GROUP)
        store.setCurrentGroup(null)
        store.assertHidden()
        assertEquals(a, store.tabs.value.selectedId)
    }

    @Test fun unlockAfterReturningToThePrivateChipShowsTheTabsAndKeepsTheNormalSelection() {
        val store = store()
        store.select(a)
        store.setCurrentGroup(PRIVATE_GROUP)
        store.unlock()
        assertFalse(store.tabs.value.privateLocked)
        assertEquals(a, store.tabs.value.selectedId)
        assertEquals(listOf(p, q), store.tabs.value.tabsIn(PRIVATE_GROUP).map { it.id })
    }

    @Test fun selectingAnotherPrivateTabAfterUnlockStaysUnlocked() {
        val store = store()
        store.select(a)
        store.setCurrentGroup(PRIVATE_GROUP)
        store.unlock()
        store.select(p)
        assertEquals(p, store.tabs.value.selectedId)
        assertFalse(store.tabs.value.privateLocked)
        store.select(q)
        assertEquals(q, store.tabs.value.selectedId)
        assertFalse(store.tabs.value.privateLocked)
        store.select(p)
        assertEquals(p, store.tabs.value.selectedId)
        assertFalse(store.tabs.value.privateLocked)
        assertEquals(PRIVATE_GROUP, store.tabs.value.currentGroup)
    }

    @Test fun backgroundOnANormalTabAfterPrivateWasUnlockedLocksWithoutResume() {
        val store = store()
        store.select(a)
        store.setCurrentGroup(PRIVATE_GROUP)
        store.unlock()
        store.setCurrentGroup(null)
        store.unlock()
        store.lock()
        assertEquals(a, store.tabs.value.selectedId)
        store.assertHidden()
        store.unlock()
        assertEquals(a, store.tabs.value.selectedId)
        assertFalse(store.tabs.value.privateResume)
    }

    @Test fun backgroundOnANormalTabWithPrivateUnlockedOnTheGridChipResumesNothingAndLocks() {
        val store = store()
        store.select(a)
        store.lock()
        store.unlock()
        store.lock()
        assertFalse(store.tabs.value.privateResume)
        assertTrue(store.tabs.value.privateLocked)
        assertEquals(a, store.tabs.value.selectedId)
    }

    @Test fun lockedStateExposesNoPrivateTabToAnyListWhenLeavingByEveryPath() {
        val paths = listOf<(TabsStore) -> Unit>(
            { it.select(a) },
            { it.setCurrentGroup(null) },
            { it.add(c) },
            { it.lock() },
            { it.setCurrentGroup(it.createGroup("G")) },
        )
        paths.forEach { path ->
            val store = store()
            path(store)
            assertTrue(store.tabs.value.privateLocked)
            assertTrue(store.tabs.value.tabs.none { it.isPrivate })
            assertTrue(store.tabs.value.selectedId !in listOf(p, q))
        }
    }
}
