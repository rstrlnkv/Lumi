package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TabGroupsTest {
    private val w = GroupId("w")
    private val r = GroupId("r")
    private val base = TabGroups().create("Work", w)!!.create("Reading", r)!!

    @Test fun createAppendsGroupsInCreationOrder() {
        assertEquals(listOf(w, r), base.groups.map { it.id })
        assertEquals(listOf("Work", "Reading"), base.groups.map { it.name })
    }

    @Test fun createRejectsAnEmptyNameAndANameTakenIgnoringCase() {
        assertNull(base.create("  ", GroupId("x")))
        assertNull(base.create(" work ", GroupId("x")))
    }

    @Test fun createDoesNotChangeTheCurrentGroup() {
        assertNull(base.current)
    }

    @Test fun renameToTheOwnNameInAnotherCaseIsAllowed() {
        assertEquals("WORK", base.rename(w, "WORK")!!.groups.first().name)
    }

    @Test fun renameToAnotherGroupsNameIsRejected() {
        assertNull(base.rename(w, "reading"))
        assertNull(base.rename(w, " "))
        assertNull(base.rename(GroupId("nope"), "Fine"))
    }

    @Test fun setCurrentIgnoresAnUnknownGroup() {
        assertEquals(w, base.setCurrent(w).current)
        assertEquals(w, base.setCurrent(w).setCurrent(GroupId("nope")).current)
        assertNull(base.setCurrent(w).setCurrent(null).current)
    }

    @Test fun removeResetsCurrentToTheSharedList() {
        val result = base.setCurrent(w).remove(w)
        assertEquals(listOf(r), result.groups.map { it.id })
        assertNull(result.current)
    }

    @Test fun removeOfAnotherGroupKeepsTheCurrentOne() {
        assertEquals(r, base.setCurrent(r).remove(w).current)
    }
}
