package ru.digitalpunk.lumi.ui.tabgrid

import kotlin.test.Test
import kotlin.test.assertEquals

class TabGridRulesTest {
    private val untitled = "New tab"

    @Test fun pageTitleWinsOverTheHost() = assertEquals("Example Domain", cardTitle("Example Domain", "https://example.com/", untitled))

    @Test fun missingTitleFallsBackToTheHost() = assertEquals("example.com", cardTitle("", "https://www.example.com/a", untitled))

    @Test fun blankTitleFallsBackToTheHost() = assertEquals("example.com", cardTitle("   ", "https://example.com", untitled))

    @Test fun blankPageWithoutTitleIsUntitled() = assertEquals(untitled, cardTitle("", "about:blank", untitled))

    @Test fun firstLetterOfTheTitleIsUpperCased() = assertEquals("E", cardInitial("example.com"))

    @Test fun emptyTitleHasNoInitial() = assertEquals("", cardInitial(""))

    @Test fun closingTheLastTabLeavesTheGridForTheNewEmptyTab() = assertEquals(CloseEffect.LeaveGridForNewTab, closeEffect(replacementCreated = true))

    @Test fun closingAnotherTabKeepsTheGridOpen() = assertEquals(CloseEffect.Stay, closeEffect(replacementCreated = false))
}

class TabCountGlyphTest {
    @Test fun countShowsAsANumber() = assertEquals("99", tabCountGlyph(99))

    @Test fun hundredAndMoreShowAsInfinity() {
        assertEquals("∞", tabCountGlyph(100))
        assertEquals("∞", tabCountGlyph(250))
    }
}

class TabGridEdgeTest {
    @Test fun glyphBoundaryIsNinetyNineThenHundred() {
        assertEquals("99", tabCountGlyph(99))
        assertEquals("∞", tabCountGlyph(100))
        assertEquals("∞", tabCountGlyph(101))
    }

    @Test fun glyphForZeroOneAndHuge() {
        assertEquals("0", tabCountGlyph(0))
        assertEquals("1", tabCountGlyph(1))
        assertEquals("∞", tabCountGlyph(Int.MAX_VALUE))
    }

    @Test fun glyphIsNeverWiderThanTwoCharacters() {
        for (n in 0..1000) assertEquals(true, tabCountGlyph(n).length <= 2, "count $n")
    }

    @Test fun titleOfOnlyWhitespaceAndNoUrlIsUntitled() = assertEquals("New tab", cardTitle("\t \n", "", "New tab"))

    @Test fun initialOfATitleStartingWithAnEmojiDoesNotSplitTheSurrogatePair() {
        val initial = cardInitial("😀 fun")
        val whole = initial.none { it.isSurrogate() } || (initial.length == 2 && Character.isSurrogatePair(initial[0], initial[1]))
        assertEquals(true, whole, "initial has a lone surrogate: ${initial.map { it.code.toString(16) }}")
    }

    @Test fun initialOfATitleWithLeadingSpaceIsNotASpace() = assertEquals(false, cardInitial(" example").isBlank())
}

class TabCardSwipeTest {
    @Test fun cardIsFullyOpaqueAtRest() = assertEquals(1f, swipeAlpha(0f, 200f, 0.4f))

    @Test fun cardReachesTheMinimumAtTheDismissPointOnEitherSide() {
        assertEquals(0.4f, swipeAlpha(100f, 200f, 0.4f), 1e-6f)
        assertEquals(0.4f, swipeAlpha(-100f, 200f, 0.4f), 1e-6f)
    }

    @Test fun cardStaysAtTheMinimumBeyondTheDismissPoint() = assertEquals(0.4f, swipeAlpha(190f, 200f, 0.4f), 1e-6f)

    @Test fun cardIsHalfwayFadedHalfwayToTheDismissPoint() = assertEquals(0.7f, swipeAlpha(50f, 200f, 0.4f), 1e-6f)

    @Test fun unmeasuredCardDoesNotFade() = assertEquals(1f, swipeAlpha(30f, 0f, 0.4f))

    @Test fun dismissPointIsHalfTheCardWidth() = assertEquals(0.5f, DISMISS_FRACTION)
}

class CardInitialTest {
    @Test fun leadingSpaceIsSkipped() = assertEquals("E", cardInitial(" example"))

    @Test fun emojiAfterASpaceStaysWhole() = assertEquals("😀", cardInitial("  😀 fun"))

    @Test fun blankTitleHasNoInitial() = assertEquals("", cardInitial(" \t"))
}
