package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlainSaveFilterTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val p = TabId("p")
    private val q = TabId("q")
    private val g = GroupId("g")

    private fun info(id: TabId, group: GroupId? = null) = TabInfo(id, "t", "https://x", false, false, group)

    private val state = TabsState(
        listOf(info(a), info(p, PRIVATE_GROUP), info(b, g), info(q, PRIVATE_GROUP)),
        selectedId = p,
        groups = listOf(TabGroup(g, "G")),
        currentGroup = PRIVATE_GROUP,
    )

    @Test fun plainSaveNeverContainsAPrivateTab() {
        val plain = plainSave(state, lastNormal = b)
        assertEquals(listOf(a, b), plain.tabs.map { it.id })
        assertTrue(plain.tabs.none { it.isPrivate })
    }

    @Test fun plainSelectionFallsBackToLastNormalWhenPrivateIsSelected() {
        assertEquals(b, plainSave(state, lastNormal = b).selected)
        assertEquals(a, plainSave(state, lastNormal = null).selected)
        assertEquals(a, plainSave(state.copy(selectedId = a), lastNormal = b).selected)
    }

    @Test fun plainCurrentGroupIsNeverPrivate() {
        assertEquals(g, plainSave(state, lastNormal = b).currentGroup)
        assertNull(plainSave(state, lastNormal = a).currentGroup)
    }

    @Test fun privateSaveHoldsOnlyPrivateTabsInOrder() {
        assertEquals(listOf(p, q), privateTabsOf(state.tabs).map { it.id })
    }
}
