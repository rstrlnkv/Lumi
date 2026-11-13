package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetAddress
import java.util.concurrent.CountDownLatch
import javax.net.ssl.SNIHostName
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inputs nobody fed [PinnedHttpsGet]: a bad first URL, odd redirects, a failing connect, cancellation mid-read. No network. */
class PinnedHttpsGetInputsTest {
    private val public4 = InetAddress.getByName("93.184.216.34")
    private val other4 = InetAddress.getByName("93.184.216.35")
    private val dns = mutableMapOf<String, List<InetAddress>>("a.com" to listOf(public4), "b.com" to listOf(other4))
    private val lookups = mutableListOf<String>()
    private val connects = mutableListOf<Pair<String, InetAddress>>()
    private var reply: (host: String, request: String) -> String = { _, _ -> "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok" }
    private var connectFailure: (InetAddress) -> Throwable? = { null }

    private val get = PinnedHttpsGet(
        resolve = { host -> lookups += host; dns[host] ?: throw java.net.UnknownHostException(host) },
        connect = { host, address ->
            connects += host to address
            connectFailure(address)?.let { throw it }
            val out = ByteArrayOutputStream()
            object : Connection {
                override val output = out
                override val input by lazy {
                    ByteArrayInputStream(reply(host, out.toString(Charsets.ISO_8859_1.name())).toByteArray(Charsets.ISO_8859_1))
                }
                override fun close() {}
            }
        },
    )

    private fun fetch(url: String, accept: String = IconFetcher.IMAGE) = runBlocking { get.get(url, accept) }

    @Test fun aFirstUrlThatIsNotHttpsOnPort443WithoutUserInfoOnAPublicNameIsNeverResolvedOrConnected() {
        listOf(
            "http://a.com/i.png", "https://a.com:8443/i.png", "https://u@a.com/i.png", "https://localhost/i.png",
            "https://127.0.0.1/i.png", "https://[::1]/i.png", "https://2130706433/i.png", "https://printer.local/i.png",
            "ftp://a.com/i.png", "file:///etc/passwd", "", "https://", "https://a.com/" + "x".repeat(2100),
        ).forEach { assertNull(fetch(it), it) }
        assertEquals(emptyList(), lookups)
        assertEquals(emptyList(), connects)
    }

    @Test fun anIpLiteralInAnyNumericSpellingIsRefusedByNameBeforeAnyLookup() {
        listOf("127.1", "0x7f.1", "0177.0.0.1", "127.0.0.1.", "10.0.0.1", "[::ffff:127.0.0.1]", "0x7f000001", "2130706433")
            .forEach { assertNull(iconTarget("https://$it/"), it) }
    }

    @Test fun aHexDottedNameThatSomeResolverReadsAsLoopbackIsStillStoppedByTheAddressCheck() {
        // The name rule lets `0x7f.0x0.0x0.0x1` through (its last label is not all digits); the resolved address must stop it.
        assertNotNull(iconTarget("https://0x7f.0x0.0x0.0x1/"))
        dns["0x7f.0x0.0x0.0x1"] = listOf(InetAddress.getByName("127.0.0.1"))
        assertNull(fetch("https://0x7f.0x0.0x0.0x1/i.png"))
        assertEquals(emptyList(), connects)
    }

    @Test fun relativeAndProtocolRelativeRedirectsAreFollowedAndEachHopIsResolvedForItsOwnHost() {
        reply = { host, request ->
            when {
                request.startsWith("GET /start") -> "HTTP/1.1 302 Found\r\nLocation: //b.com/mid\r\n\r\n"
                host == "b.com" && request.startsWith("GET /mid") -> "HTTP/1.1 302 Found\r\nLocation: HTTPS://a.com/end?q=1\r\n\r\n"
                else -> "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok"
            }
        }
        assertEquals("https://a.com/end?q=1", fetch("https://a.com/start")?.finalUrl?.replaceFirst("HTTPS", "https"))
        assertEquals(listOf("a.com", "b.com", "a.com"), lookups)
        assertEquals(listOf(public4, other4, public4), connects.map { it.second })
    }

    @Test fun aProtocolRelativeRedirectToAnIpOrAnotherPortIsRefused() {
        listOf("//127.0.0.1/x", "//a.com:8443/x", "//u@a.com/x", "//[::1]/x", "https://a.com\\@evil.com/x").forEach { location ->
            connects.clear()
            reply = { _, _ -> "HTTP/1.1 302 Found\r\nLocation: $location\r\n\r\n" }
            assertNull(fetch("https://a.com/i.png"), location)
            assertEquals(1, connects.size, location)
        }
    }

    @Test fun aRedirectWithAnEmptyMissingOrMalformedLocationGivesNullAfterAtMostSixConnections() {
        listOf("Location: \r\n", "", "Location: https://b.com/a b\r\n", "Location: https://b.com/%zz\r\n", "Location: ht!tp://x\r\n")
            .forEach { header ->
                connects.clear()
                reply = { _, _ -> "HTTP/1.1 302 Found\r\n$header\r\n" }
                assertNull(fetch("https://a.com/i.png"), header)
                assertTrue(connects.size <= 6, header)
            }
    }

    @Test fun aBodyOverTheLimitIsRefusedNotCutAndAnExactLimitBodyIsKept() {
        val big = "x".repeat(MAX_BYTES + 1)
        reply = { _, _ -> "HTTP/1.1 200 OK\r\n\r\n$big" }
        assertNull(fetch("https://a.com/i.png"))
        reply = { _, _ -> "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n${Integer.toHexString(MAX_BYTES + 1)}\r\n$big\r\n0\r\n\r\n" }
        assertNull(fetch("https://a.com/i.png"))
        val exact = "x".repeat(MAX_BYTES)
        reply = { _, _ -> "HTTP/1.1 200 OK\r\nContent-Length: $MAX_BYTES\r\n\r\n$exact" }
        assertEquals(MAX_BYTES, fetch("https://a.com/i.png")?.body?.size)
    }

    @Test fun aBodyThatEndsInsideAChunkGivesNullInsteadOfAShortImage() {
        reply = { _, _ -> "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n10\r\nabc" }
        assertNull(fetch("https://a.com/i.png"))
    }

    @Test fun aFailedFirstAddressFallsBackToTheSecondAndNeverTriesAThird() {
        val third = InetAddress.getByName("93.184.216.36")
        dns["a.com"] = listOf(public4, other4, third)
        connectFailure = { if (it == public4) IOException("refused") else null }
        assertNotNull(fetch("https://a.com/i.png"))
        assertEquals(listOf(public4, other4), connects.map { it.second })
        connects.clear()
        connectFailure = { IOException("refused") }
        assertNull(fetch("https://a.com/i.png"))
        assertEquals(listOf(public4, other4), connects.map { it.second })
    }

    @Test fun aFailedConnectToTheCheckedAddressNeverFallsBackToAnotherLookup() {
        connectFailure = { IOException("down") }
        assertNull(fetch("https://a.com/i.png"))
        assertEquals(listOf("a.com"), lookups)
    }

    /** [HttpGet] says "Null on any error". An unchecked exception from the socket layer must not escape either. */
    @Test fun anUncheckedExceptionFromTheConnectGivesNullLikeAnyOtherError() {
        connectFailure = { IllegalArgumentException("bad SNI host name") }
        assertNull(fetch("https://a.com/i.png"))
    }

    /** The reason the test above can happen on a real name: [SNIHostName] follows the strict STD3 rules that `iconTarget` does not. */
    @Test fun anIconHostWithAnUnderscoreIsAcceptedByTheNameRuleButRejectedBySni() {
        assertNotNull(iconTarget("https://my_site.example.com/"))
        assertFailsWith<IllegalArgumentException> { SNIHostName("my_site.example.com") }
    }

    @Test fun cancellingWhileTheReadIsBlockedClosesTheConnectionAndReturnsAtOnce() = runBlocking<Unit> {
        val blocked = CountDownLatch(1)
        val closed = CompletableDeferred<Unit>()
        val closeLatch = CountDownLatch(1)
        val hanging = PinnedHttpsGet(
            resolve = { listOf(public4) },
            connect = { _, _ ->
                object : Connection {
                    override val output = ByteArrayOutputStream()
                    override val input = object : InputStream() {
                        override fun read(): Int {
                            blocked.countDown()
                            closeLatch.await()
                            throw IOException("socket closed")
                        }
                    }
                    override fun close() { closeLatch.countDown(); closed.complete(Unit) }
                }
            },
        )
        val job = async(Dispatchers.Default, start = CoroutineStart.DEFAULT) { hanging.get("https://a.com/i.png", IconFetcher.IMAGE) }
        withTimeout(5_000) { while (blocked.count > 0L) kotlinx.coroutines.yield() }
        withTimeout(5_000) { job.cancelAndJoin() }
        withTimeout(5_000) { closed.await() }
    }

    @Test fun aHeadCutOffBeforeItsEndAndAHeadWithOnlyAStatusLineGiveNullWithoutHanging() {
        listOf("HTTP/1.1 200 OK\r\nContent-Le", "HTTP/1.1 200 OK\r\n", "HTTP/1.1 200\r\n\r\nok").forEach { text ->
            reply = { _, _ -> text }
            val answer = fetch("https://a.com/i.png")
            if (text.endsWith("ok")) assertContentEquals("ok".toByteArray(), answer?.body) else assertNull(answer, text)
        }
    }

    @Test fun cancellingWhileTheLookupIsBlockedReturnsAtOnceAndNeverConnects() = runBlocking<Unit> {
        val looking = CountDownLatch(1)
        val release = CountDownLatch(1)
        val connected = CountDownLatch(1)
        val hanging = PinnedHttpsGet(
            resolve = { looking.countDown(); release.await(); listOf(public4) },
            connect = { _, _ -> connected.countDown(); throw IOException("unused") },
        )
        val job = async(Dispatchers.Default) { hanging.get("https://a.com/i.png", IconFetcher.IMAGE) }
        try {
            withTimeout(5_000) { while (looking.count > 0L) kotlinx.coroutines.yield() }
            withTimeout(5_000) { job.cancelAndJoin() }
        } finally {
            release.countDown()
        }
        assertTrue(!connected.await(500, java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test fun aConnectionThatOpensAfterTheCancelIsClosedAndNeverWrittenTo() = runBlocking<Unit> {
        val connecting = CountDownLatch(1)
        val release = CountDownLatch(1)
        val closed = CompletableDeferred<Unit>()
        val out = ByteArrayOutputStream()
        val hanging = PinnedHttpsGet(
            resolve = { listOf(public4) },
            connect = { _, _ ->
                connecting.countDown()
                release.await()
                object : Connection {
                    override val output = out
                    override val input = ByteArrayInputStream(ByteArray(0))
                    override fun close() { closed.complete(Unit) }
                }
            },
        )
        val job = async(Dispatchers.Default) { hanging.get("https://a.com/i.png", IconFetcher.IMAGE) }
        try {
            withTimeout(5_000) { while (connecting.count > 0L) kotlinx.coroutines.yield() }
            withTimeout(5_000) { job.cancelAndJoin() }
        } finally {
            release.countDown()
        }
        withTimeout(5_000) { closed.await() }
        assertEquals(0, out.size())
    }
}
