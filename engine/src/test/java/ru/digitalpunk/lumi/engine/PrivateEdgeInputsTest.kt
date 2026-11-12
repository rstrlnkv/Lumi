package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inputs the M6b tests did not feed: the last private tab, a lock under the open private grid, a second lock, a cancelled unlock. */
class PrivateEdgeInputsTest {
    private val a = TabId("a")
    private val p = TabId("p")
    private val q = TabId("q")

    private fun store(): TabsStore = TabsStore().apply {
        add(a)
        add(p, private = true)
    }

    @Test fun closingTheLastPrivateTabLeavesNothingLockedOrPending() {
        val store = store()
        store.close(p)
        val state = store.tabs.value
        assertEquals(0, state.privateCount)
        assertFalse(state.privateLocked)
        assertFalse(state.privateResume)
        assertEquals(a, state.selectedId)
        assertEquals(listOf(a), state.tabs.map { it.id })
    }

    @Test fun aPrivateTabAddedAfterTheLastOneClosedOpensWithoutAnUnlock() {
        val store = store()
        store.lock()
        store.unlock()
        store.close(p)
        store.lock()
        store.add(q, private = true)
        assertEquals(q, store.tabs.value.selectedId)
        assertFalse(store.tabs.value.privateLocked)
        assertEquals(listOf(a, q), store.tabs.value.tabs.map { it.id })
    }

    @Test fun lockUnderTheOpenPrivateGridKeepsTheChipCountAndHidesTheTabs() {
        val store = store()
        store.select(a)
        store.setCurrentGroup(PRIVATE_GROUP)
        store.lock()
        val state = store.tabs.value
        assertEquals(PRIVATE_GROUP, state.currentGroup)
        assertTrue(state.privateLocked)
        assertEquals(1, state.privateCount)
        assertTrue(state.tabsIn(PRIVATE_GROUP).isEmpty())
        assertEquals(a, state.selectedId)
        assertFalse(state.privateResume)
    }

    @Test fun lockUnderTheOpenPrivateGridThenUnlockKeepsTheNormalTabInFront() {
        val store = store()
        store.select(a)
        store.setCurrentGroup(PRIVATE_GROUP)
        store.lock()
        store.unlock()
        assertEquals(a, store.tabs.value.selectedId)
        assertEquals(listOf(a, p), store.tabs.value.tabs.map { it.id })
    }

    @Test fun lockingTwiceKeepsTheResumeOfThePrivateTabThatWasInFront() {
        val store = store()
        store.lock()
        store.lock()
        assertTrue(store.tabs.value.privateResume)
        store.unlock()
        assertEquals(p, store.tabs.value.selectedId)
    }

    @Test fun gridMovedAwayFromPrivateWhileLockedDropsTheResumeAndUnlockKeepsTheNormalTab() {
        val store = store()
        store.lock()
        assertTrue(store.tabs.value.privateResume)
        store.setCurrentGroup(null)
        assertFalse(store.tabs.value.privateResume)
        store.unlock()
        assertEquals(a, store.tabs.value.selectedId)
        assertNull(store.tabs.value.currentGroup)
    }

    @Test fun cancelledUnlockLeavesTheTabsHiddenAndTheResumePending() {
        val store = store()
        store.lock()
        store.select(p)
        assertTrue(store.tabs.value.privateLocked)
        assertEquals(a, store.tabs.value.selectedId)
        assertTrue(store.tabs.value.privateResume)
        assertTrue(store.tabs.value.tabs.none { it.isPrivate })
    }

    @Test fun leavingPrivateWhileLockedMeansUnlockDoesNotJumpBackToIt() {
        val store = store()
        store.lock()
        store.leavePrivate()
        store.unlock()
        assertEquals(a, store.tabs.value.selectedId)
        assertFalse(store.tabs.value.privateResume)
    }

    @Test fun aSecondPrivateTabWhileHiddenTabsAreLockedDoesNotUnlockThem() {
        val store = store()
        store.lock()
        store.add(q, private = true)
        assertTrue(store.tabs.value.privateLocked)
        assertTrue(store.tabs.value.tabs.none { it.isPrivate })
        assertEquals(2, store.tabs.value.privateCount)
    }

    @Test fun droppingThePrivateTabsWhileLockedClearsLockAndResume() {
        val store = store()
        store.lock()
        assertEquals(listOf(p), store.dropPrivate())
        val state = store.tabs.value
        assertEquals(0, state.privateCount)
        assertFalse(state.privateLocked)
        assertFalse(state.privateResume)
        assertEquals(listOf(a), state.tabs.map { it.id })
        assertNull(store.privateIds().firstOrNull())
    }
}
