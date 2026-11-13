package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IconFetcherTest {
    private val target = IconTarget("a.com", "https://a.com/")

    private class FakeHttp(val pages: Map<String, HttpResponse?>, val hang: Set<String> = emptySet(), val fail: Set<String> = emptySet()) : HttpGet {
        val asked = mutableListOf<String>()
        override suspend fun get(url: String, accept: String): HttpResponse? {
            asked += url
            if (url in hang) awaitCancellation()
            if (url in fail) throw IOException("timeout")
            return pages[url]
        }
    }

    /** Encodes a body that starts with "ok" to "png:<body>"; anything else does not decode. */
    private val codec = object : IconCodec {
        override fun encode(bytes: ByteArray): ByteArray? = String(bytes).takeIf { it.startsWith("ok") }?.let { "png:$it".toByteArray() }
    }

    private fun page(html: String) = HttpResponse("https://a.com/", html.toByteArray())
    private fun img(body: String, url: String) = HttpResponse(url, body.toByteArray())

    @Test fun aFailedPageStillTriesFaviconIco() = runBlocking<Unit> {
        val http = FakeHttp(mapOf("https://a.com/favicon.ico" to img("ok-ico", "https://a.com/favicon.ico")))
        assertContentEquals("png:ok-ico".toByteArray(), IconFetcher(http, codec).fetch(target))
        assertEquals(listOf("https://a.com/", "https://a.com/favicon.ico"), http.asked)
    }

    @Test fun theFirstCandidateThatEncodesWinsAndLaterOnesAreNotRequested() = runBlocking<Unit> {
        val http = FakeHttp(mapOf(
            "https://a.com/" to page("""<link rel="apple-touch-icon" href="/t.png"><link rel="icon" href="/i.png">"""),
            "https://a.com/t.png" to img("ok-touch", "https://a.com/t.png"),
            "https://a.com/i.png" to img("ok-icon", "https://a.com/i.png"),
        ))
        assertContentEquals("png:ok-touch".toByteArray(), IconFetcher(http, codec).fetch(target))
        assertEquals(listOf("https://a.com/", "https://a.com/t.png"), http.asked)
    }

    @Test fun aCandidateThatDoesNotEncodeFallsToTheNext() = runBlocking<Unit> {
        val http = FakeHttp(mapOf(
            "https://a.com/" to page("""<link rel="apple-touch-icon" href="/t.png">"""),
            "https://a.com/t.png" to img("garbage", "https://a.com/t.png"),
            "https://a.com/favicon.ico" to img("ok-ico", "https://a.com/favicon.ico"),
        ))
        assertContentEquals("png:ok-ico".toByteArray(), IconFetcher(http, codec).fetch(target))
    }

    @Test fun linksResolveAgainstTheFinalUrlOfThePage() = runBlocking<Unit> {
        val http = FakeHttp(mapOf(
            "https://a.com/" to HttpResponse("https://www.a.com/home/", """<link rel="icon" href="i.png">""".toByteArray()),
            "https://www.a.com/home/i.png" to img("ok-www", "https://www.a.com/home/i.png"),
        ))
        assertContentEquals("png:ok-www".toByteArray(), IconFetcher(http, codec).fetch(target))
    }

    @Test fun anExceptionFromTheNetworkGivesNullNotAThrow() = runBlocking<Unit> {
        val http = FakeHttp(emptyMap(), fail = setOf("https://a.com/", "https://a.com/favicon.ico"))
        assertNull(IconFetcher(http, codec).fetch(target))
    }

    @Test fun anExceptionFromTheCodecGivesNull() = runBlocking<Unit> {
        val http = FakeHttp(mapOf("https://a.com/favicon.ico" to img("ok", "https://a.com/favicon.ico")))
        val throwing = object : IconCodec { override fun encode(bytes: ByteArray): ByteArray? = throw IllegalStateException("bad") }
        assertNull(IconFetcher(http, throwing).fetch(target))
    }

    @Test fun theTotalDeadlineEndsAHangingFetchWithNull() = runBlocking<Unit> {
        val http = FakeHttp(emptyMap(), hang = setOf("https://a.com/"))
        assertNull(IconFetcher(http, codec, totalTimeoutMs = 50).fetch(target))
    }

    @Test fun cancellationOfTheCallerIsNotSwallowed() = runBlocking<Unit> {
        val http = object : HttpGet { override suspend fun get(url: String, accept: String): HttpResponse? = throw CancellationException("gone") }
        val result = runCatching { IconFetcher(http, codec).fetch(target) }
        assertEquals(true, result.exceptionOrNull() is CancellationException)
    }
}
