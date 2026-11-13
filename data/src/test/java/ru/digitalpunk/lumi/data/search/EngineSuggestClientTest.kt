package ru.digitalpunk.lumi.data.search

import kotlinx.coroutines.runBlocking
import ru.digitalpunk.lumi.data.icons.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** A fake [SuggestGet]: nothing here touches the network. */
class EngineSuggestClientTest {
    private class Call(val url: String, val language: String, val maxBytes: Int, val maxRedirects: Int, val maxLive: Int)

    private val calls = mutableListOf<Call>()
    private var body: ByteArray? = """["git",["github"]]""".toByteArray()
    private val client = EngineSuggestClient(object : SuggestGet {
        override suspend fun getCapped(url: String, accept: String, acceptLanguage: String, maxBytes: Int, maxRedirects: Int, maxLive: Int): HttpResponse? {
            calls += Call(url, acceptLanguage, maxBytes, maxRedirects, maxLive)
            return body?.let { HttpResponse(url, it) }
        }
    })

    private fun suggest(engine: SearchEngine = SearchEngine.Google, tag: String = "ru-RU") = runBlocking { client.suggest(engine, "git", tag) }

    @Test fun aCallGoesToTheEngineWithTheLanguageTheCapOneRedirectAndALiveLimit() {
        assertEquals(listOf("github"), suggest())
        val call = calls.single()
        assertEquals(SearchEngine.Google.suggestUrl("git"), call.url)
        assertEquals("ru-RU,ru;q=0.9,en;q=0.5", call.language)
        assertEquals(64 * 1024, call.maxBytes)
        assertEquals(1, call.maxRedirects)
        assertEquals(3, call.maxLive)
    }

    @Test fun invalidUtf8NoAnswerOrABadShapeGivesNull() {
        body = byteArrayOf('['.code.toByte(), '"'.code.toByte(), 0xC3.toByte(), 0x28, '"'.code.toByte(), ']'.code.toByte())
        assertNull(suggest())
        body = null
        assertNull(suggest())
        body = "<html>".toByteArray()
        assertNull(suggest())
    }

    @Test fun acceptLanguageComesFromTheLocaleTagAndKeepsOnlySafeCharacters() {
        assertEquals("ru-RU,ru;q=0.9,en;q=0.5", acceptLanguage("ru-RU"))
        assertEquals("en-US,en;q=0.9", acceptLanguage("en-US"))
        assertEquals("ru,en;q=0.5", acceptLanguage("ru"))
        assertEquals("en", acceptLanguage("und"))
        assertEquals("en", acceptLanguage(""))
        assertEquals("deCH,en;q=0.5", acceptLanguage("de\r\nCH"))
    }
}
