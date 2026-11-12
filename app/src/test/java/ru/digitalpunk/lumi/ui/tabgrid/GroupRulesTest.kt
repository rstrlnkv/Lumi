package ru.digitalpunk.lumi.ui.tabgrid

import ru.digitalpunk.lumi.engine.GroupId
import ru.digitalpunk.lumi.engine.TabGroup
import ru.digitalpunk.lumi.engine.TabId
import ru.digitalpunk.lumi.engine.TabInfo
import ru.digitalpunk.lumi.engine.TabsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupRulesTest {
    private val w = TabGroup(GroupId("w"), "Work")
    private val r = TabGroup(GroupId("r"), "Reading")

    @Test fun blankNameIsEmptyAndNeedsNoMessage() {
        assertEquals(NameProblem.Empty, groupNameProblem("  \n ", emptyList(), "Tabs"))
    }

    @Test fun nameOfAnotherGroupIsADuplicateIgnoringCaseAndSpaces() {
        assertEquals(NameProblem.Duplicate, groupNameProblem(" work ", listOf("Work"), "Tabs"))
    }

    @Test fun reservedNameOfTheSharedListIsADuplicate() {
        assertEquals(NameProblem.Duplicate, groupNameProblem("TABS", emptyList(), "Tabs"))
    }

    @Test fun freshNameHasNoProblem() {
        assertNull(groupNameProblem("My Work", listOf("Work"), "Tabs"))
    }

    @Test fun moveRowsListEveryGroupAndDisableTheTabsOwn() {
        val rows = moveRows(listOf(w, r), w.id)
        assertEquals(listOf(w, r), rows.map { it.group })
        assertEquals(listOf(true, false), rows.map { it.isCurrent })
    }

    @Test fun moveRowsOfASharedTabDisableNone() {
        assertTrue(moveRows(listOf(w, r), null).none { it.isCurrent })
    }

    @Test fun removeFromGroupIsOfferedOnlyForGroupedTabs() {
        assertTrue(showsRemoveFromGroup(w.id))
        assertFalse(showsRemoveFromGroup(null))
    }

    @Test fun deletingAnEmptyGroupAsksNothing() {
        assertFalse(deleteNeedsConfirmation(0))
        assertTrue(deleteNeedsConfirmation(1))
    }

    @Test fun chipsListTheSharedListFirstThenGroupsEachWithItsCount() {
        fun info(id: String, g: GroupId?) = TabInfo(TabId(id), "", "", false, false, g)
        val state = TabsState(listOf(info("a", null), info("b", w.id), info("c", w.id)), TabId("a"), listOf(w, r), null)
        assertEquals(
            listOf(ChipModel(null, null, 1), ChipModel(w.id, "Work", 2), ChipModel(r.id, "Reading", 0)),
            chips(state),
        )
    }

    @Test fun chipFullyInsideTheViewportNeedsNoScroll() {
        assertFalse(chipNeedsScroll(offset = 0, size = 100, viewportStart = 0, viewportEnd = 300))
        assertFalse(chipNeedsScroll(offset = 200, size = 100, viewportStart = 0, viewportEnd = 300))
    }

    @Test fun chipCutAtEitherEdgeOrOutsideNeedsScroll() {
        assertTrue(chipNeedsScroll(offset = -1, size = 100, viewportStart = 0, viewportEnd = 300))
        assertTrue(chipNeedsScroll(offset = 201, size = 100, viewportStart = 0, viewportEnd = 300))
        assertTrue(chipNeedsScroll(offset = 400, size = 100, viewportStart = 0, viewportEnd = 300))
    }

    @Test fun tabCountIsTheCurrentGroupsOwnTabs() {
        fun info(id: String, g: GroupId?) = TabInfo(TabId(id), "", "", false, false, g)
        val tabs = listOf(info("a", null), info("b", w.id), info("c", w.id), info("d", r.id))
        assertEquals(2, currentGroupTabCount(TabsState(tabs, TabId("b"), listOf(w, r), w.id)))
        assertEquals(1, currentGroupTabCount(TabsState(tabs, TabId("a"), listOf(w, r), null)))
        assertEquals(0, currentGroupTabCount(TabsState(tabs, TabId("a"), listOf(w, r, TabGroup(GroupId("x"), "Empty")), GroupId("x"))))
    }
}
