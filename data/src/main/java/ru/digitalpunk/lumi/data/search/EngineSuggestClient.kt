package ru.digitalpunk.lumi.data.search

import ru.digitalpunk.lumi.data.icons.HttpResponse
import ru.digitalpunk.lumi.data.icons.PinnedHttpsGet
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** A capped GET with a language, a redirect limit and a limit on requests still running; [PinnedHttpsGet] in the app. */
interface SuggestGet {
    suspend fun getCapped(url: String, accept: String, acceptLanguage: String, maxBytes: Int, maxRedirects: Int, maxLive: Int): HttpResponse?
}

internal const val SUGGEST_MAX_BYTES = 64 * 1024
private const val SUGGEST_MAX_REDIRECTS = 1
private const val SUGGEST_MAX_LIVE = 3
private const val ACCEPT = "application/x-suggestions+json, application/json;q=0.9, */*;q=0.1"

/**
 * Asks a search engine for suggestions (ADR 009): no cookies, no Referer, the app language, at most [SUGGEST_MAX_BYTES].
 * Its only caller is the address bar's suggestion pipeline, which runs it only while the person's switch is on.
 */
class EngineSuggestClient(private val http: SuggestGet = PinnedHttpsGet()) {
    /** The engine's suggestions for [query], or null on any failure: no answer, invalid UTF-8 or another shape. */
    suspend fun suggest(engine: SearchEngine, query: String, languageTag: String): List<String>? {
        val body = http.getCapped(
            engine.suggestUrl(query), ACCEPT, acceptLanguage(languageTag), SUGGEST_MAX_BYTES, SUGGEST_MAX_REDIRECTS, SUGGEST_MAX_LIVE,
        )?.body ?: return null
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(body)).toString()
        } catch (_: CharacterCodingException) {
            return null
        }
        return parseSuggestions(text)
    }
}

/** `ru-RU` → `ru-RU,ru;q=0.9,en;q=0.5`: the tag, its language, then English. Only ASCII letters, digits and `-` are kept. */
internal fun acceptLanguage(tag: String): String {
    val clean = tag.filter { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '-' }.take(35)
    val primary = clean.substringBefore('-')
    if (primary.isEmpty() || primary.equals("und", ignoreCase = true)) return "en"
    return buildList {
        add(clean)
        if (primary != clean) add("$primary;q=0.9")
        if (!primary.equals("en", ignoreCase = true)) add("en;q=0.5")
    }.joinToString(",")
}
