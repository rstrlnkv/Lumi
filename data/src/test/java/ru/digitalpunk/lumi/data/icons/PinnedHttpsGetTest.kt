package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.runBlocking
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Fake resolver and fake sockets: no test here touches the network. */
class PinnedHttpsGetTest {
    private val public4 = InetAddress.getByName("93.184.216.34")
    private val dns = mutableMapOf<String, List<InetAddress>>("a.com" to listOf(public4))
    private val connects = mutableListOf<Pair<String, InetAddress>>()
    private val requests = mutableListOf<String>()
    private var closes = 0
    private var reply: (host: String, request: String) -> String = { _, _ -> "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok" }

    private val get = PinnedHttpsGet(
        resolve = { host -> dns[host] ?: throw java.net.UnknownHostException(host) },
        connect = { host, address ->
            connects += host to address
            val out = ByteArrayOutputStream()
            object : Connection {
                override val output = out
                override val input by lazy {
                    val request = out.toString(Charsets.ISO_8859_1.name()).also { requests += it }
                    ByteArrayInputStream(reply(host, request).toByteArray(Charsets.ISO_8859_1))
                }
                override fun close() { closes++ }
            }
        },
    )

    private fun fetch(url: String) = runBlocking { get.get(url, IconFetcher.IMAGE) }

    @Test fun aPublicNameWithAnyLocalRecordIsNeverConnected() {
        listOf(
            listOf(InetAddress.getByName("10.0.0.5")),
            listOf(InetAddress.getByName("::1")),
            listOf(InetAddress.getByName("::ffff:127.0.0.1")),
            listOf(public4, InetAddress.getByName("192.168.1.1")),
            emptyList(),
        ).forEach { records ->
            dns["a.com"] = records
            assertNull(fetch("https://a.com/i.png"), records.toString())
        }
        assertEquals(emptyList(), connects)
    }

    @Test fun theSocketGoesToTheCheckedAddressWithTheRealHostForTlsAndTheHostHeader() {
        val response = assertNotNull(fetch("https://a.com/i.png?s=1"))
        assertEquals(listOf("a.com" to public4), connects)
        assertTrue(requests.single().startsWith("GET /i.png?s=1 HTTP/1.1\r\nHost: a.com\r\n"), requests.single())
        assertContentEquals("ok".toByteArray(), response.body)
        assertEquals("https://a.com/i.png?s=1", response.finalUrl)
    }

    @Test fun aRedirectToANameThatResolvesLocalStopsAfterTheFirstConnection() {
        dns["evil.com"] = listOf(InetAddress.getByName("127.0.0.1"))
        reply = { _, _ -> "HTTP/1.1 302 Found\r\nLocation: https://evil.com/x\r\n\r\n" }
        assertNull(fetch("https://a.com/i.png"))
        assertEquals(1, connects.size)
    }

    @Test fun redirectsToHttpAnotherPortOrUserInfoAreRefused() {
        listOf("http://a.com/x", "https://a.com:444/x", "https://u@a.com/x", "https://u:p@a.com/x").forEach { location ->
            connects.clear()
            reply = { _, _ -> "HTTP/1.1 301 Moved\r\nLocation: $location\r\n\r\n" }
            assertNull(fetch("https://a.com/i.png"), location)
            assertEquals(1, connects.size, location)
        }
    }

    @Test fun fiveRedirectsAreFollowedAndASixthIsNot() {
        fun chain(hops: Int): (String, String) -> String = { _, request ->
            val n = Regex("GET /(\\d+)").find(request)!!.groupValues[1].toInt()
            if (n < hops) "HTTP/1.1 307 Temp\r\nLocation: /${n + 1}\r\n\r\n" else "HTTP/1.1 200 OK\r\nContent-Length: 1\r\n\r\nx"
        }
        reply = chain(5)
        assertEquals("https://a.com/5", fetch("https://a.com/0")?.finalUrl)
        assertEquals(6, connects.size)
        connects.clear()
        reply = chain(6)
        assertNull(fetch("https://a.com/0"))
        assertEquals(6, connects.size)
    }

    @Test fun eachHopIsResolvedAgainSoARebindAfterTheFirstHopIsCaught() {
        var lookups = 0
        val rebinding = PinnedHttpsGet(
            resolve = { if (lookups++ == 0) listOf(public4) else listOf(InetAddress.getByName("169.254.169.254")) },
            connect = { host, address ->
                connects += host to address
                object : Connection {
                    override val output = ByteArrayOutputStream()
                    override val input = ByteArrayInputStream("HTTP/1.1 302 Found\r\nLocation: /meta\r\n\r\n".toByteArray())
                    override fun close() {}
                }
            },
        )
        assertNull(runBlocking { rebinding.get("https://a.com/", IconFetcher.IMAGE) })
        assertEquals(2, lookups)
        assertEquals(1, connects.size)
    }

    @Test fun aChunkedOkBodyIsReturned() {
        reply = { _, _ -> "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3\r\nabc\r\n0\r\n\r\n" }
        assertContentEquals("abc".toByteArray(), fetch("https://a.com/i.png")?.body)
    }

    @Test fun aNonOkStatusOrABrokenHeadGivesNullAndEveryConnectionIsClosed() {
        listOf("HTTP/1.1 404 Not Found\r\n\r\n", "HTTP/1.1 304 Not Modified\r\nLocation: /x\r\n\r\n", "garbage", "").forEach { text ->
            reply = { _, _ -> text }
            assertNull(fetch("https://a.com/i.png"), text)
        }
        reply = { _, _ -> "HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok" }
        assertNotNull(fetch("https://a.com/i.png"))
        assertEquals(5, connects.size)
        assertEquals(5, closes)
    }

    @Test fun aNameThatDoesNotResolveGivesNull() = assertNull(fetch("https://nowhere.example/i.png"))
}
