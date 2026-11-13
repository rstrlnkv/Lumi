package ru.digitalpunk.lumi.library

import ru.digitalpunk.lumi.data.library.BookmarkNodeEntity
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import java.time.LocalDate
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryRulesTest {
    private fun folder(id: String, parent: String = ROOT, title: String = id) =
        BookmarkNodeEntity(id, parent, BookmarkNodeEntity.FOLDER, title, null, 0, 0)

    private val nodes = listOf(folder(FAVORITES, title = ""), folder("work"), folder("docs", "work"), folder("deep", "docs"))

    @Test fun backClosesSearchFirstThenGoesUpThenClosesTheLibrary() {
        assertEquals(LibraryBack.CloseSearch, backTarget("docs", searching = true, nodes))
        assertEquals(LibraryBack.Up("work"), backTarget("docs", searching = false, nodes))
        assertEquals(LibraryBack.Up(ROOT), backTarget("work", searching = false, nodes))
        assertEquals(LibraryBack.Close, backTarget(ROOT, searching = false, nodes))
    }

    @Test fun backClosesSearchEvenAtTheRootOfTheLibrary() =
        assertEquals(LibraryBack.CloseSearch, backTarget(ROOT, searching = true, nodes))

    @Test fun backFromAFolderThatWasDeletedGoesToTheRoot() =
        assertEquals(LibraryBack.Up(ROOT), backTarget("gone", searching = false, nodes))

    @Test fun movingAFolderDisablesItselfAndItsSubtreeOnly() {
        assertEquals(setOf("work", "docs", "deep"), disabledMoveTargets(nodes, "work"))
        assertEquals(setOf("deep"), disabledMoveTargets(nodes, "deep"))
    }

    @Test fun favoritesKeepsItsLocalizedNameUntilRenamed() {
        assertEquals("Избранное", folderName(folder(FAVORITES, title = ""), "Избранное"))
        assertEquals("Mine", folderName(folder(FAVORITES, title = "Mine"), "Избранное"))
        assertEquals("work", folderName(folder("work"), "Избранное"))
    }

    @Test fun onlyAWebPageThatDidNotCrashCanBeBookmarked() {
        assertTrue(canBookmark("https://a.com/", crashed = false))
        assertTrue(canBookmark("HTTP://a.com/", crashed = false))
        assertFalse(canBookmark("https://a.com/", crashed = true))
        listOf("about:blank", "", "file:///a", "data:text/html,x").forEach { assertFalse(canBookmark(it, crashed = false), it) }
    }

    @Test fun anOlderDayIsTheWeekdayAndDateWithACapitalFirstLetter() {
        val monday = LocalDate.parse("2026-11-09")
        assertEquals("Понедельник, 9 ноября", weekdayDate(monday, Locale.forLanguageTag("ru")))
        assertEquals("Monday, 9 November", weekdayDate(monday, Locale.ENGLISH))
    }

    @Test fun theSchemeIsLeftOutOfTheShownUrl() {
        assertEquals("a.com/x?q", withoutScheme("https://a.com/x?q"))
        assertEquals("a.com", withoutScheme("HTTP://a.com"))
        assertEquals("ftp://a.com", withoutScheme("ftp://a.com"))
    }
}
