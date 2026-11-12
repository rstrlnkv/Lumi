package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabListEdgeTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val ghost = TabId("ghost")
    private val list = TabList(listOf(a, b, c), b)

    @Test fun closingAnUnknownIdChangesNothingAndIsNotLast() {
        val result = list.close(ghost)
        assertEquals(list, result.list)
        assertFalse(result.wasLast)
    }

    @Test fun closingAnUnknownIdInAnEmptyListIsNotLast() {
        assertFalse(TabList().close(ghost).wasLast)
    }

    @Test fun selectingAnUnknownIdKeepsTheSelection() {
        assertEquals(b, list.select(ghost).selected)
    }

    @Test fun closingTheOnlyTabTwiceReportsLastOnlyOnce() {
        val first = TabList(listOf(a), a).close(a)
        assertTrue(first.wasLast)
        val second = first.list.close(a)
        assertFalse(second.wasLast)
        assertTrue(second.list.ids.isEmpty())
        assertNull(second.list.selected)
    }

    @Test fun neighbourTwoAwayReachesTheFarTabOrNull() {
        val atA = TabList(listOf(a, b, c), a)
        assertEquals(c, atA.neighbour(2))
        assertNull(atA.neighbour(3))
        assertNull(atA.neighbour(-2))
        assertEquals(a, TabList(listOf(a, b, c), c).neighbour(-2))
    }

    @Test fun neighbourOfZeroIsTheSelectedTab() {
        assertEquals(b, list.neighbour(0))
    }

    @Test fun neighbourWithNothingSelectedIsNull() {
        assertNull(TabList(listOf(a, b), null).neighbour(1))
        assertNull(TabList().neighbour(1))
    }

    @Test fun addWithNoSelectionAppendsAtTheEnd() {
        val result = TabList(listOf(a, b), null).add(c)
        assertEquals(listOf(a, b, c), result.ids)
        assertEquals(c, result.selected)
    }

    @Test fun addAfterTheLastSelectedAppends() {
        assertEquals(listOf(a, b, c, TabId("n")), TabList(listOf(a, b, c), c).add(TabId("n")).ids)
    }

    @Test fun closingSelectedFirstSelectsTheNewFirst() {
        val result = TabList(listOf(a, b, c), a).close(a)
        assertEquals(b, result.list.selected)
    }

    @Test fun closingWithNothingSelectedKeepsItNull() {
        assertNull(TabList(listOf(a, b), null).close(a).list.selected)
    }
}
