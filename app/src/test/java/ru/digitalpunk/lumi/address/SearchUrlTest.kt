package ru.digitalpunk.lumi.address

import ru.digitalpunk.lumi.data.search.SearchEngine
import kotlin.test.Test
import kotlin.test.assertEquals

class SearchUrlTest {
    @Test fun aSuggestionThatLooksLikeAnAddressIsStillSearched() {
        assertEquals("https://duckduckgo.com/?q=wikipedia.org", searchUrl(SearchEngine.DuckDuckGo.searchPrefix, "wikipedia.org"))
        assertEquals("https://yandex.ru/search/?text=a+%26+b", searchUrl(SearchEngine.Yandex.searchPrefix, "a & b"))
    }

    @Test fun theDefaultSearchIsGoogles() {
        assertEquals(SearchEngine.Google.searchPrefix, DEFAULT_SEARCH_URL)
    }

    @Test fun typedWordsSearchWithTheChosenEngine() {
        assertEquals("https://www.bing.com/search?q=kotlin+flow", resolveInput("kotlin flow", SearchEngine.Bing.searchPrefix))
    }
}
