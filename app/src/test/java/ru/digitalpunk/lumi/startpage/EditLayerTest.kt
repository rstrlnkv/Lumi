package ru.digitalpunk.lumi.startpage

import kotlin.test.Test
import kotlin.test.assertEquals

class EditLayerTest {
    private val url = "example.org/page"

    @Test fun notEditingShowsNothingForAnyText() {
        assertEquals(EditLayer.None, editLayer(editing = false, text = "", initial = "", startItems = 3))
        assertEquals(EditLayer.None, editLayer(editing = false, text = "abc", initial = url, startItems = 3))
    }

    @Test fun theUntouchedUrlShowsTheStartPage() = assertEquals(EditLayer.StartPage, editLayer(true, url, url, 3))

    @Test fun emptyTextShowsTheStartPage() = assertEquals(EditLayer.StartPage, editLayer(true, "", url, 3))

    @Test fun whitespaceOnlyTextShowsTheStartPage() = assertEquals(EditLayer.StartPage, editLayer(true, "   ", url, 3))

    @Test fun aCharacterAddedToTheUrlShowsSuggestions() = assertEquals(EditLayer.Suggestions, editLayer(true, url + "x", url, 3))

    @Test fun aTypedLetterShowsSuggestions() = assertEquals(EditLayer.Suggestions, editLayer(true, "g", url, 3))

    @Test fun anInlineCompletionShowsSuggestions() = assertEquals(EditLayer.Suggestions, editLayer(true, "github.com", url, 3))

    @Test fun textTypedThenErasedBringsTheStartPageBack() {
        assertEquals(EditLayer.Suggestions, editLayer(true, "gi", url, 3))
        assertEquals(EditLayer.StartPage, editLayer(true, "", url, 3))
    }

    @Test fun aBlankPageWithBlankTextShowsTheStartPage() = assertEquals(EditLayer.StartPage, editLayer(true, "", "", 3))

    @Test fun anEmptyStartFolderKeepsTodaysScrimAndList() {
        assertEquals(EditLayer.Suggestions, editLayer(true, "", url, 0))
        assertEquals(EditLayer.Suggestions, editLayer(true, url, url, 0))
        assertEquals(EditLayer.Suggestions, editLayer(true, "", "", 0))
    }

    @Test fun aTrailingSpaceOnTheUrlCountsAsTyping() = assertEquals(EditLayer.Suggestions, editLayer(true, "$url ", url, 3))

    @Test fun theUrlInAnotherCaseCountsAsTyping() = assertEquals(EditLayer.Suggestions, editLayer(true, url.uppercase(), url, 3))

    @Test fun anyTextOnABlankPageShowsSuggestions() = assertEquals(EditLayer.Suggestions, editLayer(true, "a", "", 3))

    @Test fun oneStartItemIsEnoughForTheStartPage() = assertEquals(EditLayer.StartPage, editLayer(true, "", url, 1))

    @Test fun tabsAndNewlinesAreBlank() = assertEquals(EditLayer.StartPage, editLayer(true, "\t\n ", url, 3))
}
