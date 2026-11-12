package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabListGroupTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val d = TabId("d")
    private val e = TabId("e")
    private val n = TabId("n")
    private val work = GroupId("work")
    private val read = GroupId("read")

    /** a(shared) b(work) c(read) d(work) e(shared). */
    private fun list(selected: TabId, groups: Map<TabId, GroupId> = mapOf(b to work, c to read, d to work)) =
        TabList(listOf(a, b, c, d, e), selected, groups)

    @Test fun newTabInTheSelectedTabsGroupGoesRightAfterTheSelectedTab() {
        val result = list(b).add(n, work)
        assertEquals(listOf(a, b, n, c, d, e), result.ids)
        assertEquals(work, result.groupOf[n])
        assertEquals(n, result.selected)
    }

    @Test fun newTabInAnotherGroupWithMembersGoesAfterThatGroupsLastTab() {
        assertEquals(listOf(a, b, c, d, n, e), list(a).add(n, work).ids)
    }

    @Test fun newTabInAnEmptyGroupGoesRightAfterTheSelectedTab() {
        val result = list(b).add(n, GroupId("empty"))
        assertEquals(listOf(a, b, n, c, d, e), result.ids)
        assertEquals(GroupId("empty"), result.groupOf[n])
    }

    @Test fun newTabInTheSharedListWhileAGroupTabIsSelectedGoesAfterTheLastSharedTab() {
        assertEquals(listOf(a, b, c, d, e, n), list(c).add(n, null).ids)
    }

    @Test fun neighbourStaysInsideTheSelectedTabsGroupWhenOtherGroupsInterleave() {
        assertEquals(d, list(b).neighbour(1))
        assertEquals(b, list(d).neighbour(-1))
        assertEquals(e, list(a).neighbour(1))
    }

    @Test fun neighbourIsNullAtTheEdgeOfAGroupEvenWhenOtherGroupsContinue() {
        assertNull(list(b).neighbour(-1))
        assertNull(list(d).neighbour(1))
        assertNull(list(c).neighbour(1))
    }

    @Test fun closingTheSelectedTabSelectsItsGroupNeighbourBeforeAnyOtherTab() {
        assertEquals(d, list(b).close(b).list.selected)
        assertEquals(b, list(d).close(d).list.selected)
    }

    @Test fun closingTheLastTabOfAGroupSelectsTheNearestSharedTabBeforeItElseAfter() {
        assertEquals(a, list(c).close(c).list.selected)
        val first = TabList(listOf(b, a), b, mapOf(b to work))
        assertEquals(a, first.close(b).list.selected)
    }

    @Test fun closingTheLastTabOfAGroupWithNoSharedTabsSelectsTheNearestTabOfAnotherGroup() {
        val l = TabList(listOf(b, c, d), c, mapOf(b to work, c to read, d to work))
        assertEquals(b, l.close(c).list.selected)
    }

    @Test fun closingGroupTabClearsItsGroupEntry() {
        assertTrue(n !in list(b).add(n, work).close(n).list.groupOf)
        assertTrue(b !in list(b).close(b).list.groupOf)
    }

    @Test fun moveIntoAGroupWithMembersPlacesTheTabAfterTheLastMember() {
        val result = list(a).move(a, work)
        assertEquals(listOf(b, c, d, a, e), result.ids)
        assertEquals(work, result.groupOf[a])
        assertEquals(listOf(a, c, b, d, e), list(a).move(b, read).ids)
    }

    @Test fun moveIntoAnEmptyGroupKeepsTheTabsGlobalPlace() {
        val result = list(a).move(c, GroupId("empty"))
        assertEquals(listOf(a, b, c, d, e), result.ids)
        assertEquals(GroupId("empty"), result.groupOf[c])
    }

    @Test fun moveToTheGroupTheTabAlreadyHasChangesNothing() {
        val l = list(a)
        assertEquals(l, l.move(b, work))
        assertEquals(l, l.move(a, null))
    }

    @Test fun removeFromGroupPlacesTheTabAfterTheLastSharedTab() {
        val result = list(a).move(b, null)
        assertEquals(listOf(a, c, d, e, b), result.ids)
        assertNull(result.groupOf[b])
    }

    @Test fun moveKeepsTheSelection() {
        assertEquals(b, list(b).move(b, read).selected)
    }

    @Test fun removeGroupClosesExactlyItsMembersAndKeepsTheOthersInOrder() {
        val result = list(a).removeGroup(work)
        assertEquals(listOf(a, c, e), result.list.ids)
        assertEquals(a, result.list.selected)
        assertEquals(mapOf(c to read), result.list.groupOf)
    }

    @Test fun removeGroupWhoseMemberIsSelectedSelectsTheNearestSharedTab() {
        assertEquals(a, list(d).removeGroup(work).list.selected)
    }

    @Test fun removeGroupHoldingEveryTabReportsWasLast() {
        val l = TabList(listOf(b, d), b, mapOf(b to work, d to work))
        val result = l.removeGroup(work)
        assertTrue(result.wasLast)
        assertNull(result.list.selected)
        assertTrue(result.list.ids.isEmpty())
    }

    @Test fun removeUnknownOrEmptyGroupChangesNothing() {
        val l = list(a)
        assertEquals(l, l.removeGroup(GroupId("nope")).list)
    }
}
