package ru.digitalpunk.lumi.suggest

import androidx.compose.ui.text.TextRange
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InlineCompletionTest {
    private val hosts = listOf("github.com", "gitlab.com", "git.io")

    /** Typing at the end of [prev] (caret at its end) gives [text]. */
    private fun type(prev: String, text: String, composing: Boolean = false, prevSelection: TextRange = TextRange(prev.length)) =
        inlineCompletion(prev, prevSelection, text, TextRange(text.length), composing, hosts)

    @Test fun typingCompletesToTheBestHostAndKeepsTheTypedCasing() {
        assertEquals("github.com", type("gi", "git"))
        assertEquals("GIThub.com", type("GI", "GIT"))
        assertEquals("gitlab.com", type("gitl", "gitla"))
    }

    @Test fun aDeleteAPasteOrACaretBeforeTheEndDoesNotComplete() {
        assertNull(type("gith", "git"))
        assertNull(type("", "git"))
        assertNull(inlineCompletion("gi", TextRange(2), "git", TextRange(1), false, hosts))
        assertNull(inlineCompletion("gi", TextRange(2), "git", TextRange(0, 3), false, hosts))
    }

    @Test fun textWithASpaceASlashOrAColonOrNothingDoesNotComplete() {
        assertNull(type("git", "git "))
        assertNull(type("git", "git/"))
        assertNull(type("git", "git:"))
        assertNull(inlineCompletion("", TextRange(0), "", TextRange(0), false, hosts))
    }

    @Test fun aHostEqualToTheTextOrNoMatchOrAComposingImeDoesNotComplete() {
        assertNull(type("git.i", "git.io"))
        assertNull(type("x", "xy"))
        assertNull(type("gi", "git", composing = true))
    }

    @Test fun aLetterOverTheSelectedCompletionCompletesAgainOrDropsIt() {
        // "git" with "hub.com" selected; the IME replaces the selection with the next letter.
        assertEquals("github.com", inlineCompletion("github.com", TextRange(3, 10), "gith", TextRange(4), false, hosts))
        assertNull(inlineCompletion("github.com", TextRange(3, 10), "gitx", TextRange(4), false, hosts))
        // Backspace removes only the selected completion.
        assertNull(inlineCompletion("github.com", TextRange(3, 10), "git", TextRange(3), false, hosts))
    }

    @Test fun theTypedPartLeavesOutASelectedTail() {
        assertEquals("git", typedPart("github.com", TextRange(3, 10)))
        assertEquals("github.com", typedPart("github.com", TextRange(10)))
        assertEquals("github.com", typedPart("github.com", TextRange(0, 3)))
    }
}
