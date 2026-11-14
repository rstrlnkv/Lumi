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
        val caps = mutableListOf<Int>()
        override suspend fun get(url: String, accept: String, maxBytes: Int, userAgent: String): HttpResponse? {
            asked += url
            caps += maxBytes
            if (url in hang) awaitCancellation()
            if (url in fail) throw IOException("timeout")
            return pages[url]
        }
    }

    /** Encodes a body that starts with "ok" to "png:<body>", of the size after its last `-` (else 192); anything else does not decode. */
    private val codec = IconCodec { bytes ->
        String(bytes).takeIf { it.startsWith("ok") }?.let { EncodedIcon("png:$it".toByteArray(), IconQuality(it.substringAfterLast('-').toIntOrNull() ?: 192, false)) }
    }

    private fun page(html: String) = HttpResponse("https://a.com/", html.toByteArray())
    private fun img(body: String, url: String) = HttpResponse(url, body.toByteArray())

    @Test fun aFailedPageStillTriesFaviconIco() = runBlocking<Unit> {
        val http = FakeHttp(mapOf("https://a.com/favicon.ico" to img("ok-ico", "https://a.com/favicon.ico")))
        assertContentEquals("png:ok-ico".toByteArray(), IconFetcher(http, codec).fetch(target)?.png)
        assertEquals(listOf("https://a.com/", "https://a.com/apple-touch-icon.png", "https://a.com/apple-touch-icon-precomposed.png", "https://a.com/favicon.ico"), http.asked)
    }

    @Test fun theFirstCandidateThatEncodesWinsAndLaterOnesAreNotRequested() = runBlocking<Unit> {
        val http = FakeHttp(mapOf(
            "https://a.com/" to page("""<link rel="apple-touch-icon" href="/t.png"><link rel="icon" href="/i.png">"""),
            "https://a.com/t.png" to img("ok-touch", "https://a.com/t.png"),
            "https://a.com/i.png" to img("ok-icon", "https://a.com/i.png"),
        ))
        assertContentEquals("png:ok-touch".toByteArray(), IconFetcher(http, codec).fetch(target)?.png)
        assertEquals(listOf("https://a.com/", "https://a.com/t.png"), http.asked)
    }

    @Test fun aCandidateThatDoesNotEncodeFallsToTheNext() = runBlocking<Unit> {
        val http = FakeHttp(mapOf(
            "https://a.com/" to page("""<link rel="apple-touch-icon" href="/t.png">"""),
            "https://a.com/t.png" to img("garbage", "https://a.com/t.png"),
            "https://a.com/favicon.ico" to img("ok-ico", "https://a.com/favicon.ico"),
        ))
        assertContentEquals("png:ok-ico".toByteArray(), IconFetcher(http, codec).fetch(target)?.png)
    }

    @Test fun linksResolveAgainstTheFinalUrlOfThePage() = runBlocking<Unit> {
        val http = FakeHttp(mapOf(
            "https://a.com/" to HttpResponse("https://www.a.com/home/", """<link rel="icon" href="i.png">""".toByteArray()),
            "https://www.a.com/home/i.png" to img("ok-www", "https://www.a.com/home/i.png"),
        ))
        assertContentEquals("png:ok-www".toByteArray(), IconFetcher(http, codec).fetch(target)?.png)
    }

    @Test fun anExceptionFromTheNetworkGivesNullNotAThrow() = runBlocking<Unit> {
        val http = FakeHttp(emptyMap(), fail = setOf("https://a.com/", "https://a.com/favicon.ico"))
        assertNull(IconFetcher(http, codec).fetch(target)?.png)
    }

    @Test fun anExceptionFromTheCodecGivesNull() = runBlocking<Unit> {
        val http = FakeHttp(mapOf("https://a.com/favicon.ico" to img("ok", "https://a.com/favicon.ico")))
        val throwing = object : IconCodec { override fun encode(bytes: ByteArray): EncodedIcon? = throw IllegalStateException("bad") }
        assertNull(IconFetcher(http, throwing).fetch(target))
    }

    @Test fun theTotalDeadlineEndsAHangingFetchWithNull() = runBlocking<Unit> {
        val http = FakeHttp(emptyMap(), hang = setOf("https://a.com/"))
        assertNull(IconFetcher(http, codec, totalTimeoutMs = 50).fetch(target))
    }

    @Test fun cancellationOfTheCallerIsNotSwallowed() = runBlocking<Unit> {
        val http = object : HttpGet { override suspend fun get(url: String, accept: String, maxBytes: Int, userAgent: String): HttpResponse? = throw CancellationException("gone") }
        val result = runCatching { IconFetcher(http, codec).fetch(target) }
        assertEquals(true, result.exceptionOrNull() is CancellationException)
    }

    @Test fun eachBodyIsCappedForItsKind() = runBlocking<Unit> {
        val http = FakeHttp(mapOf(
            "https://a.com/" to page("""<link rel="manifest" href="/m.json">"""),
            "https://a.com/m.json" to HttpResponse("https://a.com/m.json", """{"icons":[{"src":"/m.png","sizes":"192x192"}]}""".toByteArray()),
            "https://a.com/m.png" to img("ok-m", "https://a.com/m.png"),
        ))
        assertContentEquals("png:ok-m".toByteArray(), IconFetcher(http, codec).fetch(target)?.png)
        assertEquals(listOf("https://a.com/", "https://a.com/m.json", "https://a.com/m.png"), http.asked)
        assertEquals(listOf(262_144, 131_072, 1_048_576), http.caps)
    }

    @Test fun aLinkThatMeetsTheTargetSkipsTheManifest() = runBlocking<Unit> {
        val http = FakeHttp(mapOf(
            "https://a.com/" to page("""<link rel="manifest" href="/m.json"><link rel="apple-touch-icon" href="/t.png">"""),
            "https://a.com/t.png" to img("ok-t", "https://a.com/t.png"),
        ))
        assertContentEquals("png:ok-t".toByteArray(), IconFetcher(http, codec).fetch(target)?.png)
        assertEquals(listOf("https://a.com/", "https://a.com/t.png"), http.asked)
    }

    @Test fun aManifestIconOf512BeatsAFavicon() = runBlocking<Unit> {
        val http = FakeHttp(mapOf(
            "https://a.com/" to page("""<link rel="icon" href="/favicon.ico"><link rel="manifest" href="/m.json">"""),
            "https://a.com/m.json" to HttpResponse("https://a.com/m.json", """{"icons":[{"src":"/512.png","sizes":"512x512"}]}""".toByteArray()),
            "https://a.com/512.png" to img("ok-512", "https://a.com/512.png"),
            "https://a.com/favicon.ico" to img("ok-ico", "https://a.com/favicon.ico"),
        ))
        assertContentEquals("png:ok-512".toByteArray(), IconFetcher(http, codec).fetch(target)?.png)
    }
}
