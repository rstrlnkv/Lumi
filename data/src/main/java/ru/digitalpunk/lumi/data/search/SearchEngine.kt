package ru.digitalpunk.lumi.data.search

import java.net.URLEncoder

/**
 * The search engines offered in Settings, in their menu order. [searchPrefix] plus the encoded query is a search;
 * [suggestTemplate] is the suggestion endpoint with `%s` for the encoded query (OpenSearch `["query",[...]]`).
 */
enum class SearchEngine(val id: String, val domain: String, val searchPrefix: String, val suggestTemplate: String) {
    Google("google", "google.com", "https://www.google.com/search?q=", "https://suggestqueries.google.com/complete/search?client=firefox&q=%s"),
    DuckDuckGo("duckduckgo", "duckduckgo.com", "https://duckduckgo.com/?q=", "https://duckduckgo.com/ac/?q=%s&type=list"),
    Yandex("yandex", "yandex.ru", "https://yandex.ru/search/?text=", "https://suggest.yandex.ru/suggest-ff.cgi?part=%s"),
    Bing("bing", "bing.com", "https://www.bing.com/search?q=", "https://api.bing.com/osjson.aspx?query=%s"),
    Startpage("startpage", "startpage.com", "https://www.startpage.com/sp/search?query=", "https://www.startpage.com/osuggestions?q=%s"),
    Ecosia("ecosia", "ecosia.org", "https://www.ecosia.org/search?q=", "https://ac.ecosia.org/autocomplete?q=%s&type=list"),
    ;

    /** The suggestion URL for the first [MAX_QUERY] characters of [query]; encoding keeps `/ ? # @` from changing the host. */
    fun suggestUrl(query: String): String = suggestTemplate.replace("%s", URLEncoder.encode(query.take(MAX_QUERY), "UTF-8"))

    companion object {
        const val MAX_QUERY = 100

        /** [Google] for null, blank or an unknown id. */
        fun fromId(id: String?): SearchEngine = entries.firstOrNull { it.id == id } ?: Google
    }
}
