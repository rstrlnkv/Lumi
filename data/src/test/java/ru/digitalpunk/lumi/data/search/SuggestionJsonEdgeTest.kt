package ru.digitalpunk.lumi.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SuggestionJsonEdgeTest {
    @Test fun anEscapedSurrogatePairDecodesToOneEmoji() {
        assertEquals(listOf("😀"), parseSuggestions("""["q",["😀"]]"""))
    }

    @Test fun aSpacedEmptyListIsEmptyNotNull() {
        assertEquals(emptyList(), parseSuggestions(""" [ "q" , [ ] ] """))
    }

    @Test fun aUnicodeEscapeWithASignInsteadOfFourHexDigitsIsABadEscape() {
        assertNull(parseSuggestions("""["q",["a\u+123b"]]"""))
        assertNull(parseSuggestions("""["q",["a\u-001b"]]"""))
    }

    @Test fun bidiAndOtherFormatCharactersAreDroppedFromAnItem() {
        assertEquals(listOf("abc"), parseSuggestions("""["q",["a\u202Eb\u2066c\u200F"]]"""))
    }
}
