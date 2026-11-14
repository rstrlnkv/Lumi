package ru.digitalpunk.lumi.data.search

import ru.digitalpunk.lumi.data.icons.HttpResponse
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** A capped GET without cookies, with a language, in the private context when [private]; Gecko's fetch in the app (ADR 014). */
interface SuggestGet {
    suspend fun getCapped(url: String, accept: String, acceptLanguage: String, maxBytes: Int, private: Boolean): HttpResponse?
}

internal const val SUGGEST_MAX_BYTES = 64 * 1024
private const val ACCEPT = "application/x-suggestions+json, application/json;q=0.9, */*;q=0.1"

/**
 * Asks a search engine for suggestions (ADR 009): no cookies, no Referer, the app language, at most [SUGGEST_MAX_BYTES].
 * Its only caller is the address bar's suggestion pipeline, which runs it only while the person's switch is on.
 */
class EngineSuggestClient(private val http: SuggestGet) {
    /** The engine's suggestions for [query], or null on any failure: no answer, invalid UTF-8 or another shape. [private]: asked from a private tab. */
    suspend fun suggest(engine: SearchEngine, query: String, languageTag: String, private: Boolean): List<String>? {
        val body = http.getCapped(
            engine.suggestUrl(query), ACCEPT, acceptLanguage(languageTag), SUGGEST_MAX_BYTES, private,
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
