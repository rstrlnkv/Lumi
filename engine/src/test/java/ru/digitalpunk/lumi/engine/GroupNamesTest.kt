package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GroupNamesTest {
    @Test fun normalizeTrimsBothEndsAndTurnsLineBreaksIntoSpaces() {
        assertEquals("a b c", normalizeGroupName("  a\nb\r\nc  "))
    }

    @Test fun sanitizeKeepsTrailingSpacesSoTheUserCanTypeTwoWords() {
        assertEquals("My ", sanitizeGroupInput("My "))
    }

    @Test fun sanitizeCapsAtTwentyCodePointsAndNeverSplitsASurrogatePair() {
        val twenty = "😀".repeat(20)
        assertEquals(twenty, sanitizeGroupInput(twenty))
        assertEquals(twenty, sanitizeGroupInput(twenty + "😀"))
        assertEquals(20, MAX_GROUP_NAME_LENGTH)
    }

    @Test fun blankNameNormalizesToEmpty() {
        assertEquals("", normalizeGroupName(" \n \t "))
    }

    @Test fun takenIgnoresCaseAndSurroundingSpaces() {
        assertTrue(groupNameTaken(" work ", listOf("Work")))
        assertFalse(groupNameTaken("Play", listOf("Work")))
    }
}
