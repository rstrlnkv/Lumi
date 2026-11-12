package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestorePrivateTest {
    private val a = TabId("a")
    private val p = TabId("p")
    private val q = TabId("q")

    @Test fun restoredPrivateTabsAppendAfterNormalTabsAndStayLocked() {
        val store = TabsStore()
        store.restore(listOf(RestoredTab(a, "https://a", "a")), a)
        store.restorePrivate(listOf(RestoredTab(p, "https://p", "p"), RestoredTab(q, "https://q", "q")))
        store.unlock()
        assertEquals(listOf(a, p, q), store.tabs.value.tabs.map { it.id })
        assertEquals(a, store.tabs.value.selectedId)
    }

    @Test fun privateTabOpenedBeforeTheRestoreFinishesIsLockedWithTheStoredOnes() {
        val store = TabsStore()
        store.restore(listOf(RestoredTab(a, "https://a", "a")), a)
        store.add(p, private = true)
        store.restorePrivate(listOf(RestoredTab(q, "https://q", "q")))
        assertEquals(true, store.tabs.value.privateLocked)
        assertEquals(a, store.tabs.value.selectedId)
        store.unlock()
        assertEquals(p, store.tabs.value.selectedId)
    }

    @Test fun restoringNoStoredPrivateTabsLeavesAnOpenPrivateTabUnlocked() {
        val store = TabsStore()
        store.restore(listOf(RestoredTab(a, "https://a", "a")), a)
        store.add(p, private = true)
        store.restorePrivate(emptyList())
        assertEquals(false, store.tabs.value.privateLocked)
        assertEquals(p, store.tabs.value.selectedId)
    }

    @Test fun restoredPrivateTabWithUnloadableUrlComesBackBlank() {
        val items = privateRestoreItems(listOf(SavedTab(p, "javascript:alert(1)", "t", "{}")), emptySet())
        assertEquals("about:blank", items.single().url)
        assertNull(items.single().sessionState)
    }

    @Test fun duplicateOrClashingPrivateIdsAreDropped() {
        val items = privateRestoreItems(
            listOf(SavedTab(a, "https://a", "t", null), SavedTab(p, "https://p", "t", null), SavedTab(p, "https://p2", "t", null)),
            existing = setOf(a),
        )
        assertEquals(listOf(p), items.map { it.id })
        assertEquals("https://p", items.single().url)
    }
}
