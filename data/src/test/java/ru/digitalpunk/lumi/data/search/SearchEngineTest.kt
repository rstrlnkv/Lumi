package ru.digitalpunk.lumi.data.search

import ru.digitalpunk.lumi.data.icons.isAllowedIconUrl
import java.net.URI
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SearchEngineTest {
    @Test fun everyEngineSearchesAndSuggestsOverHttpsToAPublicNameTheIconRulesAllow() {
        assertEquals(listOf("google", "duckduckgo", "yandex", "bing", "startpage", "ecosia"), SearchEngine.entries.map { it.id })
        for (engine in SearchEngine.entries) {
            assertTrue(engine.searchPrefix.endsWith("="), engine.id)
            assertTrue(isAllowedIconUrl(engine.searchPrefix + "x"), engine.id)
            val suggest = engine.suggestUrl("x")
            assertTrue(isAllowedIconUrl(suggest), engine.id)
        }
    }

    @Test fun anUnknownBlankOrMissingIdIsGoogle() {
        assertEquals(SearchEngine.Google, SearchEngine.fromId(null))
        assertEquals(SearchEngine.Google, SearchEngine.fromId(""))
        assertEquals(SearchEngine.Google, SearchEngine.fromId("altavista"))
        assertEquals(SearchEngine.Yandex, SearchEngine.fromId("yandex"))
    }

    @Test fun theQueryIsPercentEncodedSoItCannotChangeTheHostOrPath() {
        val url = SearchEngine.DuckDuckGo.suggestUrl("a&b#c?d/e@evil.com%привет")
        val uri = URI(url)
        assertEquals("duckduckgo.com", uri.host)
        assertEquals("/ac/", uri.rawPath)
        assertEquals(null, uri.rawFragment)
        assertTrue(url.contains("a%26b%23c%3Fd%2Fe%40evil.com%25%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82"), url)
    }

    @Test fun anOverLongQuerySendsOnlyItsFirstHundredCharacters() {
        val url = SearchEngine.Bing.suggestUrl("q".repeat(500))!!
        assertEquals(100, url.count { it == 'q' } - "https://api.bing.com/osjson.aspx?query=".count { it == 'q' })
    }
}
