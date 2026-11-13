package ru.digitalpunk.lumi.address

import ru.digitalpunk.lumi.data.search.SearchEngine
import kotlin.test.Test
import kotlin.test.assertEquals

/** A search with every engine, spelled out in full, for a query with a space, an ampersand and a non-ASCII letter. */
class SearchUrlEnginesTest {
    private val q = "a b&ü"
    private val enc = "a+b%26%C3%BC"

    @Test fun eachEngineSearchesWithTheEncodedQuery() {
        val expected = mapOf(
            SearchEngine.Google to "https://www.google.com/search?q=$enc",
            SearchEngine.DuckDuckGo to "https://duckduckgo.com/?q=$enc",
            SearchEngine.Yandex to "https://yandex.ru/search/?text=$enc",
            SearchEngine.Bing to "https://www.bing.com/search?q=$enc",
            SearchEngine.Startpage to "https://www.startpage.com/sp/search?query=$enc",
            SearchEngine.Ecosia to "https://www.ecosia.org/search?q=$enc",
        )
        assertEquals(SearchEngine.entries.toSet(), expected.keys)
        for ((engine, url) in expected) {
            assertEquals(url, searchUrl(engine.searchPrefix, q), engine.id)
            assertEquals(url, resolveInput(q, engine.searchPrefix), engine.id)
        }
    }

    @Test fun aCompletedHostOpensAsAnAddressNotASearchAndKeepsTheTypedCase() {
        assertEquals("https://github.com", resolveInput("github.com"))
        assertEquals("https://GIThub.com", resolveInput("GIThub.com"))
        assertEquals("https://git.io", resolveInput("git.io"))
        assertEquals("https://пример.рф", resolveInput("пример.рф"))
    }

    @Test fun aCompletedLocalHostLosesThePortOfTheHistoryEntryItCameFrom() {
        // Pins today's behaviour: the completion list holds bare hosts, so Enter on "localhost" opens port 80.
        assertEquals("http://localhost", resolveInput("localhost"))
        assertEquals("http://[::1]", resolveInput("[::1]"))
    }
}
