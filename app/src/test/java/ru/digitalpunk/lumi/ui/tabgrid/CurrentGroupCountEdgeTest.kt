package ru.digitalpunk.lumi.ui.tabgrid

import ru.digitalpunk.lumi.engine.GroupId
import ru.digitalpunk.lumi.engine.TabGroup
import ru.digitalpunk.lumi.engine.TabId
import ru.digitalpunk.lumi.engine.TabInfo
import ru.digitalpunk.lumi.engine.TabsState
import kotlin.test.Test
import kotlin.test.assertEquals

class CurrentGroupCountEdgeTest {
    private val w = TabGroup(GroupId("w"), "Work")
    private val r = TabGroup(GroupId("r"), "Reading")
    private fun info(id: String, g: GroupId?) = TabInfo(TabId(id), "", "", false, false, g)

    @Test fun groupWithOneTabCountsOne() {
        val s = TabsState(listOf(info("a", null), info("b", w.id), info("c", r.id), info("d", r.id)), TabId("b"), listOf(w, r), w.id)
        assertEquals(1, currentGroupTabCount(s))
    }

    @Test fun sharedListCountsOnlyUngroupedTabsNotAllTabs() {
        val s = TabsState(listOf(info("a", null), info("b", w.id), info("c", r.id)), TabId("a"), listOf(w, r), null)
        assertEquals(1, currentGroupTabCount(s))
    }

    @Test fun noTabsAtAllCountsZero() {
        assertEquals(0, currentGroupTabCount(TabsState(emptyList(), null, emptyList(), null)))
    }

    @Test fun hundredTabsInGroupShowInfinityGlyphWhileOthersStayBelow() {
        val tabs = (1..100).map { info("w$it", w.id) } + info("s", null)
        val s = TabsState(tabs, TabId("w1"), listOf(w), w.id)
        assertEquals(100, currentGroupTabCount(s))
        assertEquals("∞", tabCountGlyph(currentGroupTabCount(s)))
        assertEquals("1", tabCountGlyph(currentGroupTabCount(s.copy(currentGroup = null))))
    }

    @Test fun ninetyNineTabsInGroupShowNumberEvenWhenTotalIsOver100() {
        val tabs = (1..99).map { info("w$it", w.id) } + (1..5).map { info("s$it", null) }
        val s = TabsState(tabs, TabId("w1"), listOf(w), w.id)
        assertEquals("99", tabCountGlyph(currentGroupTabCount(s)))
    }
}
