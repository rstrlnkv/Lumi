package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The suggestion path of the pinned client: fake resolver and sockets, no network. */
class PinnedHttpsGetCappedTest {
    private val public4 = InetAddress.getByName("93.184.216.34")
    private val requests = mutableListOf<String>()
    private var connects = 0
    private var reply: (String) -> String = { "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok" }
    private var block: CountDownLatch? = null

    private val get = PinnedHttpsGet(
        resolve = { block?.await(5, TimeUnit.SECONDS); listOf(public4) },
        connect = { _, _ ->
            connects++
            val out = ByteArrayOutputStream()
            object : Connection {
                override val output = out
                override val input by lazy {
                    val request = out.toString(Charsets.ISO_8859_1.name()).also { requests += it }
                    ByteArrayInputStream(reply(request).toByteArray(Charsets.ISO_8859_1))
                }
                override fun close() {}
            }
        },
    )

    private fun capped(url: String = "https://a.com/s?q=1", maxBytes: Int = 4, maxRedirects: Int = 1, maxLive: Int = 3) =
        runBlocking { get.getCapped(url, "application/json", "ru-RU,ru;q=0.9", maxBytes, maxRedirects, maxLive) }

    @Test fun theRequestCarriesAcceptLanguageAndTheBodyComesBack() {
        assertContentEquals("ok".toByteArray(), capped()!!.body)
        assertTrue(requests.single().contains("\r\nAccept-Language: ru-RU,ru;q=0.9\r\n"), requests.single())
    }

    @Test fun aBodyOverTheCapGivesNullEvenWhenItContainsBody() {
        reply = { "HTTP/1.1 200 OK\r\n\r\n[\"<body>\",[]]" }
        assertNull(capped(maxBytes = 5))
        assertEquals("[\"<body>\",[]]", String(capped(maxBytes = 64)!!.body))
    }

    @Test fun aRedirectPastTheLimitGivesNull() {
        reply = { r -> if (r.startsWith("GET /s")) "HTTP/1.1 302 Found\r\nLocation: https://a.com/t\r\n\r\n" else "HTTP/1.1 302 Found\r\nLocation: https://a.com/u\r\n\r\n" }
        assertNull(capped(maxRedirects = 1))
        assertEquals(2, connects)
    }

    @Test fun anAcceptLanguageThatCouldBreakTheHeadIsRefused() {
        assertFailsWith<IllegalArgumentException> { buildRequest("a.com", "/", "*/*", "en\r\nCookie: x") }
        assertFailsWith<IllegalArgumentException> { buildRequest("a.com", "/", "*/*", "") }
        assertFalseContains(String(buildRequest("a.com", "/", "*/*"), Charsets.ISO_8859_1), "Accept-Language")
    }

    @Test fun atTheLiveLimitNoConnectionStartsAndACancelledCallFreesItsSlotWhenItsThreadReturns() = runBlocking<Unit> {
        val latch = CountDownLatch(1).also { block = it }
        val started = CompletableDeferred<Unit>()
        val first = async(Dispatchers.Default) { started.complete(Unit); get.getCapped("https://a.com/1", "*/*", "en", 64, 1, 1) }
        started.await()
        while (get.live.get() == 0) Thread.sleep(5)
        assertNull(get.getCapped("https://a.com/2", "*/*", "en", 64, 1, 1))
        assertEquals(0, connects)
        first.cancel()
        assertEquals(1, get.live.get())
        latch.countDown()
        withTimeout(5_000) { while (get.live.get() != 0) Thread.sleep(5) }
        block = null
        assertContentEquals("ok".toByteArray(), get.getCapped("https://a.com/3", "*/*", "en", 64, 1, 1)!!.body)
    }

    private fun assertFalseContains(text: String, part: String) = assertTrue(part !in text, text)
}
