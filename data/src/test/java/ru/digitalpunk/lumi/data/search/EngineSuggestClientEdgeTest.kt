package ru.digitalpunk.lumi.data.search

import kotlinx.coroutines.runBlocking
import ru.digitalpunk.lumi.data.icons.HttpResponse
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EngineSuggestClientEdgeTest {
    private var accept = ""
    private var body: ByteArray? = null
    private val client = EngineSuggestClient(object : SuggestGet {
        override suspend fun getCapped(url: String, accept: String, acceptLanguage: String, maxBytes: Int, maxRedirects: Int, maxLive: Int): HttpResponse? {
            this@EngineSuggestClientEdgeTest.accept = accept
            return body?.let { HttpResponse(url, it) }
        }
    })

    private fun suggest() = runBlocking { client.suggest(SearchEngine.Google, "git", "en") }

    @Test fun theRequestAsksForSuggestionJsonFirst() {
        body = """["git",[]]""".toByteArray()
        assertEquals(emptyList(), suggest())
        assertEquals("application/x-suggestions+json, application/json;q=0.9, */*;q=0.1", accept)
    }

    @Test fun anInvalidByteInsideAnOtherwiseValidAnswerRejectsTheWholeAnswer() {
        body = "[\"q\",[\"a".toByteArray() + byteArrayOf(0xC3.toByte(), 0x28) + "b\"]]".toByteArray()
        assertNull(suggest())
    }

    @Test fun anEmptyBodyGivesNull() {
        body = ByteArray(0)
        assertNull(suggest())
    }

    @Test fun aVeryLongTagIsCutAndACommaOrSemicolonCannotAddLanguages() {
        assertEquals("a".repeat(35), acceptLanguage("a".repeat(100)).substringBefore(","))
        assertEquals("ruevil,en;q=0.5", acceptLanguage("ru,evil"))
        assertEquals("ruq0,en;q=0.5", acceptLanguage("ru;q=0"))
    }
}
