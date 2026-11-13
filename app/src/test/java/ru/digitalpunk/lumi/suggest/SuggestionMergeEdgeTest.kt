package ru.digitalpunk.lumi.suggest

import kotlin.test.Test
import kotlin.test.assertEquals

class SuggestionMergeEdgeTest {
    private fun h(n: Int) = Suggestion.History("", "https://h$n.com/")

    @Test fun moreThanThreeLocalRowsAreCutToTheFirstThreeInOrder() {
        assertEquals(listOf(h(1), h(2), h(3), Suggestion.Search("x")), mergeSuggestions("q", (1..5).map(::h), listOf("x")))
    }

    @Test fun noEngineRowsLeavesTheLocalRowsAndNothingAtAllLeavesNothing() {
        assertEquals(listOf(h(1)), mergeSuggestions("q", listOf(h(1)), emptyList()))
        assertEquals(emptyList(), mergeSuggestions("q", emptyList(), emptyList()))
    }

    @Test fun repeatsAreCountedAfterDroppingSoFiveDistinctRowsStillComeThroughNoise() {
        val engine = listOf("a", "A", "a", "", "b", "c", "q", "d", "e", "f")
        assertEquals(listOf("a", "b", "c", "d", "e"), mergeSuggestions("q", emptyList(), engine).map { (it as Suggestion.Search).text })
    }
}
