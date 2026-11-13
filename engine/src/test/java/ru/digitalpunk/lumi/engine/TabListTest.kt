package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TabListTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val n = TabId("n")

    private fun list(selected: TabId) = TabList(listOf(a, b, c), selected)

    @Test fun addInsertsAfterSelectedAndSelectsIt() {
        val result = list(a).add(n)
        assertEquals(listOf(a, n, b, c), result.ids)
        assertEquals(n, result.selected)
    }

    @Test fun addToEmptyListSelectsIt() {
        val result = TabList().add(n)
        assertEquals(listOf(n), result.ids)
        assertEquals(n, result.selected)
    }

    @Test fun closingSelectedMiddleSelectsNext() {
        val result = list(b).close(b)
        assertEquals(listOf(a, c), result.list.ids)
        assertEquals(c, result.list.selected)
        assertFalse(result.wasLast)
    }

    @Test fun closingSelectedLastSelectsPrevious() {
        assertEquals(b, list(c).close(c).list.selected)
    }

    @Test fun closingOnlyTabReportsWasLast() {
        val result = TabList(listOf(a), a).close(a)
        assertTrue(result.wasLast)
        assertTrue(result.list.ids.isEmpty())
        assertNull(result.list.selected)
    }

    @Test fun closingUnselectedKeepsSelection() {
        val result = list(c).close(a)
        assertEquals(listOf(b, c), result.list.ids)
        assertEquals(c, result.list.selected)
    }

    @Test fun neighbourAtEndsIsNull() {
        assertNull(list(a).neighbour(-1))
        assertNull(list(c).neighbour(1))
    }

    @Test fun neighbourInTheMiddleFollowsOffset() {
        assertEquals(c, list(b).neighbour(1))
        assertEquals(a, list(b).neighbour(-1))
    }

    @Test fun selectChangesOnlyTheSelection() {
        val result = list(a).select(c)
        assertEquals(listOf(a, b, c), result.ids)
        assertEquals(c, result.selected)
    }
}

class TabListBackgroundAddTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val n = TabId("n")
    private val g = GroupId("g")

    @Test fun addWithoutSelectKeepsTheSelection() {
        val result = TabList(listOf(a, b, c), a).add(n, select = false)
        assertEquals(listOf(a, n, b, c), result.ids)
        assertEquals(a, result.selected)
    }

    @Test fun addWithAnchorGoesRightAfterTheAnchorInItsGroup() {
        val result = TabList(listOf(a, b, c), a).add(n, select = false, anchor = b)
        assertEquals(listOf(a, b, n, c), result.ids)
        assertEquals(a, result.selected)
    }

    @Test fun addToAGroupWhoseAnchorIsElsewhereGoesAfterTheGroupsLastTab() {
        val list = TabList(listOf(a, b, c), a, mapOf(b to g))
        val result = list.add(n, group = g, select = false, anchor = a)
        assertEquals(listOf(a, b, n, c), result.ids)
        assertEquals(g, result.groupOf[n])
        assertEquals(a, result.selected)
    }

    @Test fun addWithoutAnchorAppends() {
        val result = TabList(listOf(a, b), null).add(n, select = false, anchor = null)
        assertEquals(listOf(a, b, n), result.ids)
        assertEquals(null, result.selected)
    }
}
