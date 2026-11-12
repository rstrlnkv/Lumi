package ru.digitalpunk.lumi.address

import java.net.URLEncoder

const val DEFAULT_SEARCH_URL = "https://www.google.com/search?q=" // M7 moves this to settings

private val WEB_URL_WITH_HOST = Regex("^https?://[^/?#]", RegexOption.IGNORE_CASE)
private val PLAIN_HTTP_HOST = Regex("""^(localhost|\d{1,3}(\.\d{1,3}){3}|\[[0-9a-fA-F:.]+])(:\d+)?([/?#].*)?$""")
private val DOTTED_HOST =
    Regex("""^([^\s@/?#]+@)?([\p{L}\p{N}-]+\.)+(xn--[a-zA-Z0-9-]+|\p{L}{2,})\.?(:\d+)?([/?#].*)?$""")

/**
 * Turns what the user typed into a URL to load, or null when there is nothing to load.
 * Only `about:blank` is loaded from the `about:` family; [debugPages] also allows `about:crashcontent`.
 */
fun resolveInput(input: String, searchUrl: String = DEFAULT_SEARCH_URL, debugPages: Boolean = false): String? {
    val text = input.trim()
    return when {
        text.isEmpty() -> null
        text.any { it.isWhitespace() } -> searchUrl + URLEncoder.encode(text, "UTF-8")
        text.lowercase() == "about:blank" || (debugPages && text.lowercase() == "about:crashcontent") -> text
        WEB_URL_WITH_HOST.containsMatchIn(text) -> text
        PLAIN_HTTP_HOST.matches(text) -> "http://$text"
        DOTTED_HOST.matches(text) -> "https://$text"
        else -> searchUrl + URLEncoder.encode(text, "UTF-8")
    }
}
