package ru.digitalpunk.lumi.suggest

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InlineCompletionEdgeTest {
    private fun type(prev: String, text: String, hosts: List<String>, prevSelection: TextRange = TextRange(prev.length)) =
        inlineCompletion(prev, prevSelection, text, TextRange(text.length), false, hosts)

    @Test fun anEditThatAddsTwoCharactersCompletesAndOneThatAddsThreeDoesNot() {
        val hosts = listOf("github.com")
        assertEquals("github.com", type("g", "git", hosts))
        assertNull(type("g", "gith", hosts))
    }

    @Test fun aLetterTypedOverASelectedWholeAddressStartsANewCompletion() {
        assertEquals("github.com", type("https://x.org", "g", listOf("github.com"), prevSelection = TextRange(0, 13)))
    }

    @Test fun whitespaceASlashOrAColonInTheTextStopsTheCompletionEvenWhenAHostStartsWithIt() {
        assertNull(type("a", "a ", listOf("a b.com")))
        assertNull(type("a", "a/", listOf("a/b.com")))
        assertEquals("[::1]", type("", "[", listOf("[::1]")))
        assertNull(type("[", "[:", listOf("[::1]")))
    }

    @Test fun anEmptyHostListNeverCompletes() {
        assertNull(type("g", "gi", emptyList()))
    }

    @Test fun theBestHostWinsEvenWhenALaterOneAlsoMatches() {
        assertEquals("github.com", type("gi", "git", listOf("github.com", "gitlab.com")))
        assertEquals("gitlab.com", type("gi", "git", listOf("gitlab.com", "github.com")))
    }
}
