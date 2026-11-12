package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RestoreRulesTest {
    private fun tab(url: String, state: String? = "{}") = SavedTab(TabId("a"), url, "t", state)

    @Test fun loadableUrlKeepsItsStoredState() {
        assertEquals(tab("https://example.org"), tab("https://example.org").sanitized())
    }

    @Test fun unloadableUrlBecomesBlankAndDropsTheState() {
        listOf("javascript:alert(1)", "file:///etc/passwd", "about:config", "").forEach {
            val out = tab(it).sanitized()
            assertEquals("about:blank", out.url, it)
            assertNull(out.sessionState, it)
        }
    }

    @Test fun parserThatThrowsGivesNull() {
        assertNull(parseState<String>("x", { error("garbage") }) { false })
    }

    @Test fun missingBlankOrEmptyStateGivesNull() {
        assertNull(parseState<String>(null, { it }) { false })
        assertNull(parseState<String>("", { it }) { false })
        assertNull(parseState<String>("x", { null }) { false })
        assertNull(parseState<String>("x", { it }) { true })
    }

    @Test fun parsedNonEmptyStateIsReturned() {
        assertEquals("x", parseState<String>("x", { it }) { false })
    }
}
