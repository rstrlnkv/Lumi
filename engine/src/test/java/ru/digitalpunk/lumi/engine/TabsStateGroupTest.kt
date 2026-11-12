package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabsStateGroupTest {
    private val w = GroupId("w")
    private fun info(id: String, group: GroupId?) = TabInfo(TabId(id), "", "", false, false, group)

    private val state = TabsState(
        listOf(info("a", null), info("b", w), info("c", null), info("d", w)),
        TabId("b"),
        listOf(TabGroup(w, "Work")),
        w,
    )

    @Test fun neighbourHostsStayInsideTheCurrentGroup() {
        assertEquals(TabId("d"), state.neighbour(1)?.id)
        assertNull(state.neighbour(-1))
        assertEquals(TabId("c"), state.copy(selectedId = TabId("a")).neighbour(1)?.id)
    }

    @Test fun tabsInReturnsOnlyThatGroupInOrder() {
        assertEquals(listOf("b", "d"), state.tabsIn(w).map { it.id.value })
        assertEquals(listOf("a", "c"), state.tabsIn(null).map { it.id.value })
    }
}
