package ru.digitalpunk.lumi.data.search

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Every engine's suggestion URL spelled out in full: a typo in one table row must not hide behind a shared rule. */
class SearchEngineUrlsTest {
    // "a b&ü" is a space, an ampersand and a non-ASCII letter in one query.
    private val enc = "a+b%26%C3%BC"
    private val q = "a b&ü"

    @Test fun eachEnginesSuggestionUrlCarriesTheEncodedSpaceAmpersandAndNonAsciiLetter() {
        assertEquals("https://suggestqueries.google.com/complete/search?client=firefox&q=$enc", SearchEngine.Google.suggestUrl(q))
        assertEquals("https://duckduckgo.com/ac/?q=$enc&type=list", SearchEngine.DuckDuckGo.suggestUrl(q))
        assertEquals("https://suggest.yandex.ru/suggest-ff.cgi?part=$enc", SearchEngine.Yandex.suggestUrl(q))
        assertEquals("https://api.bing.com/osjson.aspx?query=$enc", SearchEngine.Bing.suggestUrl(q))
        assertEquals("https://www.startpage.com/osuggestions?q=$enc", SearchEngine.Startpage.suggestUrl(q))
        assertEquals("https://ac.ecosia.org/autocomplete?q=$enc&type=list", SearchEngine.Ecosia.suggestUrl(q))
    }

    @Test fun eachEnginesSearchPrefixIsSpelledOutInFull() {
        assertEquals("https://www.google.com/search?q=", SearchEngine.Google.searchPrefix)
        assertEquals("https://duckduckgo.com/?q=", SearchEngine.DuckDuckGo.searchPrefix)
        assertEquals("https://yandex.ru/search/?text=", SearchEngine.Yandex.searchPrefix)
        assertEquals("https://www.bing.com/search?q=", SearchEngine.Bing.searchPrefix)
        assertEquals("https://www.startpage.com/sp/search?query=", SearchEngine.Startpage.searchPrefix)
        assertEquals("https://www.ecosia.org/search?q=", SearchEngine.Ecosia.searchPrefix)
    }

    @Test fun aQueryOfExactlyAHundredCharactersIsSentWhole() {
        val url = SearchEngine.Google.suggestUrl("q".repeat(100))
        assertEquals(100, url.removePrefix("https://suggestqueries.google.com/complete/search?client=firefox&q=").length)
    }

    @Test fun aLoneSurrogateInTheQueryNeverThrows() {
        assertTrue(SearchEngine.Google.suggestUrl("ab\uD83D").startsWith("https://suggestqueries.google.com/"))
    }
}
