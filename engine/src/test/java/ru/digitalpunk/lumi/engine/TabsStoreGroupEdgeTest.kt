package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inputs the group tests did not feed: boundaries, empty groups, odd names, restore order. */
class TabsStoreGroupEdgeTest {
    private val a = TabId("a")
    private val b = TabId("b")
    private val c = TabId("c")
    private val d = TabId("d")

    private fun store(vararg ids: String) = TabsStore().apply { ids.forEach { add(TabId(it)) } }
    private fun TabsStore.ids() = tabs.value.tabs.map { it.id }

    @Test fun movingTheSelectedTabOutOfTheCurrentGroupKeepsItSelectedAndKeepsTheGridOnThatGroup() {
        val s = store("a", "b")
        val g = s.createGroup("Work")!!
        s.moveTab(b, g)
        s.setCurrentGroup(g)
        assertTrue(s.moveTab(b, null))
        assertEquals(b, s.tabs.value.selectedId)
        assertNull(s.tabs.value.tabs.first { it.id == b }.groupId)
        assertEquals(g, s.tabs.value.currentGroup)
        s.showSelectedGroup()
        assertNull(s.tabs.value.currentGroup)
    }

    @Test fun deletingTheCurrentGroupWithTheSelectedTabLeavesASelectedTabAndASharedCurrentGroup() {
        val s = store("a", "b", "c")
        val g = s.createGroup("Work")!!
        s.moveTab(b, g)
        s.select(b)
        assertFalse(s.deleteGroup(g))
        val st = s.tabs.value
        assertEquals(listOf(a, c), s.ids())
        assertEquals(a, st.selectedId)
        assertNull(st.currentGroup)
        assertTrue(st.groups.isEmpty())
    }

    @Test fun deletingTheLastGroupLeavesNoGroupsAndTheSharedTabsUntouched() {
        val s = store("a", "b")
        val g = s.createGroup("Work")!!
        s.moveTab(b, g)
        s.deleteGroup(g)
        assertEquals(listOf(a), s.ids())
        assertTrue(s.tabs.value.groups.isEmpty())
        assertFalse(s.deleteGroup(g))
        assertEquals(listOf(a), s.ids())
    }

    @Test fun deletingTheGroupHoldingEveryTabReportsLastAndLeavesNoSelection() {
        val s = store("a", "b")
        val g = s.createGroup("Work")!!
        s.moveTab(a, g)
        s.moveTab(b, g)
        assertTrue(s.deleteGroup(g))
        assertTrue(s.tabs.value.tabs.isEmpty())
        assertNull(s.tabs.value.selectedId)
        assertNull(s.neighbour(1))
    }

    @Test fun deletingAnEmptyGroupClosesNoTab() {
        val s = store("a", "b")
        val g = s.createGroup("Empty")!!
        assertFalse(s.deleteGroup(g))
        assertEquals(listOf(a, b), s.ids())
        assertEquals(b, s.tabs.value.selectedId)
    }

    @Test fun deleteGroupClearsThePagesOfItsTabsSoTheyCannotComeBack() {
        val s = store("a", "b")
        val g = s.createGroup("Work")!!
        s.moveTab(b, g)
        s.deleteGroup(g)
        s.pageChanged(b, PageState(url = "https://late.example"))
        s.titleChanged(b, "late")
        assertEquals(listOf(a), s.ids())
        assertFalse(s.moveTab(b, null))
    }

    @Test fun renamingToADuplicateOrEmptyOrWhitespaceNameKeepsTheOldName() {
        val s = store("a")
        val work = s.createGroup("Work")!!
        val home = s.createGroup("Home")!!
        assertFalse(s.renameGroup(home, " work "))
        assertFalse(s.renameGroup(home, ""))
        assertFalse(s.renameGroup(home, " \n\t "))
        assertFalse(s.renameGroup(GroupId("nope"), "X"))
        assertEquals(listOf("Work", "Home"), s.tabs.value.groups.map { it.name })
        assertTrue(s.renameGroup(work, "WORK"))
        assertEquals("WORK", s.tabs.value.groups.first { it.id == work }.name)
    }

    @Test fun emojiNamesAreAcceptedAndDuplicatesOfThemAreRefused() {
        val s = store("a")
        assertNotNull(s.createGroup("😀"))
        assertNull(s.createGroup(" 😀 "))
        assertNotNull(s.createGroup("😁"))
        assertEquals(2, s.tabs.value.groups.size)
    }

    @Test fun overlongNamesThatDifferOnlyAfterTheCapAreDuplicates() {
        val s = store("a")
        assertNotNull(s.createGroup("x".repeat(20)))
        assertNull(s.createGroup("x".repeat(25)))
    }

    @Test fun nameOfOnlyInvisibleCharactersIsNotAUsableName() {
        // U+200B zero width space, U+200D zero width joiner, U+FEFF byte order mark
        for (raw in listOf("\u200B", "\u200D", "\uFEFF", "\u200B \u200B")) {
            assertEquals("", normalizeGroupName(raw), "U+" + raw.first().code.toString(16))
        }
    }

    @Test fun nameCutByTheCapNeverEndsInAJoiner() {
        // man + joiner + woman is 3 code points; 20 is not a multiple of 3, so the cap lands right after a joiner
        val couple = "\uD83D\uDC68\u200D\uD83D\uDC69"
        val name = normalizeGroupName(couple.repeat(10))
        assertFalse(name.endsWith("\u200D"), "ends in a dangling joiner")
    }

    @Test fun zeroGroupsStateAnswersEveryQuerySafely() {
        val s = TabsStore()
        assertNull(s.neighbour(1))
        assertNull(s.neighbour(-1))
        assertTrue(s.idsIn(null).isEmpty())
        assertFalse(s.deleteGroup(GroupId("x")))
        s.setCurrentGroup(GroupId("x"))
        assertNull(s.tabs.value.currentGroup)
        assertTrue(s.tabs.value.groups.isEmpty())
    }

    @Test fun swipeAtAGroupBoundaryStopsEvenWhenOtherGroupsContinueBothWays() {
        val s = store("a", "b", "c", "d")
        val g = s.createGroup("Work")!!
        s.moveTab(b, g)
        s.moveTab(c, g)
        s.select(b)
        assertNull(s.neighbour(-1))
        assertEquals(c, s.neighbour(1))
        s.select(c)
        assertNull(s.neighbour(1))
        assertEquals(b, s.neighbour(-1))
        assertNull(s.neighbour(2))
    }

    @Test fun closingTheLastTabOfAGroupSelectsASharedTabKeepsTheGroupAndCurrentFollowsOnShow() {
        val s = store("a", "b")
        val g = s.createGroup("Work")!!
        s.moveTab(b, g)
        s.select(b)
        assertFalse(s.close(b))
        assertEquals(a, s.tabs.value.selectedId)
        assertEquals(listOf(g), s.tabs.value.groups.map { it.id })
        assertTrue(s.idsIn(g).isEmpty())
        s.showSelectedGroup()
        assertNull(s.tabs.value.currentGroup)
    }

    @Test fun closingTheOnlyTabInTheOnlyGroupReportsLast() {
        val s = store("a")
        val g = s.createGroup("Work")!!
        s.moveTab(a, g)
        assertTrue(s.close(a))
        assertNull(s.tabs.value.selectedId)
    }

    @Test fun groupWithZeroTabsRestoresAndIsListed() {
        val s = TabsStore()
        val g = GroupId("g")
        s.restore(listOf(RestoredTab(a, "u", "t")), a, listOf(TabGroup(g, "Empty")), g)
        assertEquals(listOf(g), s.tabs.value.groups.map { it.id })
        assertEquals(g, s.tabs.value.currentGroup)
        assertNotNull(s.createGroup("Other"))
        s.add(b)
        assertEquals(g, s.tabs.value.tabs.first { it.id == b }.groupId)
    }

    @Test fun restoreKeepsTheStoredTabOrderAndGroupOrderWhenGroupsInterleave() {
        val g1 = GroupId("g1")
        val g2 = GroupId("g2")
        val s = TabsStore()
        s.restore(
            listOf(RestoredTab(a, "u", "t", g2), RestoredTab(b, "u", "t"), RestoredTab(c, "u", "t", g1), RestoredTab(d, "u", "t", g2)),
            c,
            listOf(TabGroup(g2, "Two"), TabGroup(g1, "One")),
            g1,
        )
        assertEquals(listOf(a, b, c, d), s.ids())
        assertEquals(listOf(g2, g1), s.tabs.value.groups.map { it.id })
        assertEquals(listOf(a, d), s.idsIn(g2))
        s.select(a)
        assertEquals(d, s.neighbour(1))
        assertNull(s.neighbour(-1))
    }

    @Test fun restoreWithAllTabsInAMissingGroupMakesThemSharedAndSelectsTheStoredOne() {
        val s = TabsStore()
        s.restore(listOf(RestoredTab(a, "u", "t", GroupId("gone")), RestoredTab(b, "u", "t", GroupId("gone"))), b, emptyList(), GroupId("gone"))
        assertEquals(listOf(null, null), s.tabs.value.tabs.map { it.groupId })
        assertEquals(b, s.tabs.value.selectedId)
        assertNull(s.tabs.value.currentGroup)
    }

    @Test fun restoreWithNoTabsButGroupsKeepsAnEmptyListAndDoesNotCrash() {
        val s = TabsStore()
        s.restore(emptyList(), null, listOf(TabGroup(GroupId("g"), "G")), GroupId("g"))
        assertTrue(s.tabs.value.tabs.isEmpty())
        assertNull(s.tabs.value.selectedId)
        assertNull(s.neighbour(1))
    }

    @Test fun restoreDropsStoredGroupsWithDuplicateNamesIgnoringCase() {
        val s = TabsStore()
        s.restore(listOf(RestoredTab(a, "u", "t")), a, listOf(TabGroup(GroupId("1"), "Work"), TabGroup(GroupId("2"), "work")), null)
        assertEquals(1, s.tabs.value.groups.map { it.name.lowercase() }.distinct().size, "two groups the dialog would refuse")
    }
}
