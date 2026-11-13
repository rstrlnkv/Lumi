package ru.digitalpunk.lumi.suggest

import kotlin.test.Test
import kotlin.test.assertEquals

class SuggestionMergeTest {
    private val local = listOf(Suggestion.Bookmark("GitHub", "https://github.com/"), Suggestion.History("", "https://gitlab.com/"))

    @Test fun localRowsStayFirstAndUnmovedAndEngineRowsFollow() {
        assertEquals(local + Suggestion.Search("github desktop"), mergeSuggestions("git", local, listOf("github desktop")))
    }

    @Test fun engineRowsDropTheTypedTextBlanksAndCaseInsensitiveRepeats() {
        assertEquals(
            listOf(Suggestion.Search("GitHub"), Suggestion.Search("git bash")),
            mergeSuggestions(" git ", emptyList(), listOf("GIT", "GitHub", "  ", "github", "git bash ")),
        )
    }

    @Test fun atMostFiveEngineRowsAndEightRowsInAll() {
        val local3 = local + Suggestion.History("", "https://gitea.io/")
        val merged = mergeSuggestions("g", local3, (1..9).map { "g$it" })
        assertEquals(8, merged.size)
        assertEquals(local3, merged.take(3))
        assertEquals((1..5).map { Suggestion.Search("g$it") }, merged.drop(3))
    }
}
