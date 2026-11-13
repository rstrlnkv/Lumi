package ru.digitalpunk.lumi.data.icons

import java.io.ByteArrayInputStream
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HttpWireTest {
    private fun input(text: String) = ByteArrayInputStream(text.toByteArray(Charsets.ISO_8859_1))
    private fun body(response: String): ByteArray? {
        val stream = input(response)
        val head = readHead(stream) ?: return null
        return bodyStream(head, stream)?.let { readBounded(it, MAX_BYTES, ReadMode.Image) }
    }

    @Test fun theRequestIsAPlainGetWithoutCookiesOrReferer() {
        val text = String(buildRequest("a.com", "/i.png?x=1", "image/*"), Charsets.ISO_8859_1)
        assertEquals(
            "GET /i.png?x=1 HTTP/1.1\r\nHost: a.com\r\nUser-Agent: Lumi\r\nAccept: image/*\r\n" +
                "Accept-Encoding: identity\r\nConnection: close\r\n\r\n",
            text,
        )
        assertFalse("cookie" in text.lowercase() || "referer" in text.lowercase())
    }

    @Test fun aPathWithASpaceCrOrLfCannotBuildARequest() =
        listOf("/a b", "/a\r\nX: y", "/a\n").forEach { assertFailsWith<IllegalArgumentException>(it) { buildRequest("a.com", it, "*/*") } }

    @Test fun theHeadGivesTheStatusAndLowerCasedHeaderNames() {
        val head = assertNotNull(readHead(input("HTTP/1.1 301 Moved\r\nLocation: /x\r\nContent-Length:  0 \r\n\r\nrest")))
        assertEquals(301, head.status)
        assertEquals("/x", head.headers["location"])
        assertEquals("0", head.headers["content-length"])
    }

    @Test fun aHeadThatIsNotHttp1OrIsTooLongOrHasTooManyLinesOrNeverEndsIsNull() {
        assertNull(readHead(input("SSH-2.0-x\r\n\r\n")))
        assertNull(readHead(input("HTTP/2 200\r\n\r\n")))
        assertNull(readHead(input("HTTP/1.1 abc\r\n\r\n")))
        assertNull(readHead(input("HTTP/1.1 200 OK\r\nX: " + "a".repeat(17_000) + "\r\n\r\n")))
        assertNull(readHead(input("HTTP/1.1 200 OK\r\n" + "X: y\r\n".repeat(101) + "\r\n")))
        assertNull(readHead(input("HTTP/1.1 200 OK\r\nX: y\r\n")))
    }

    @Test fun aChunkedBodyIsDecodedWithExtensionsAndTrailersIgnored() =
        assertContentEquals(
            "hello world".toByteArray(),
            body("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n5;ext=1\r\nhello\r\n6\r\n world\r\n0\r\nTrailer: x\r\n\r\n"),
        )

    @Test fun aBadChunkSizeOrAMissingChunkEndThrows() {
        listOf("zz\r\nhello\r\n0\r\n\r\n", "123456789\r\n", "5\r\nhelloXX0\r\n\r\n", "5\r\nhel").forEach { chunks ->
            val stream = input("HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n$chunks")
            val decoded = bodyStream(readHead(stream)!!, stream)!!
            assertFailsWith<IOException>(chunks) { decoded.readBytes() }
        }
    }

    @Test fun aContentLengthBodyIsCutAtItsLength() =
        assertContentEquals("abc".toByteArray(), body("HTTP/1.1 200 OK\r\nContent-Length: 3\r\n\r\nabcdef"))

    @Test fun aContentLengthOverTheLimitOrInvalidGivesNoBody() {
        listOf("1048577", "-1", "x").forEach { length ->
            val stream = input("HTTP/1.1 200 OK\r\nContent-Length: $length\r\n\r\nabc")
            assertNull(bodyStream(readHead(stream)!!, stream), length)
        }
    }

    @Test fun aCompressedBodyGivesNoBody() {
        val stream = input("HTTP/1.1 200 OK\r\nContent-Encoding: gzip\r\n\r\nabc")
        assertNull(bodyStream(readHead(stream)!!, stream))
        val plain = input("HTTP/1.1 200 OK\r\nContent-Encoding: identity\r\n\r\nabc")
        assertNotNull(bodyStream(readHead(plain)!!, plain))
    }

    @Test fun aBodyWithoutLengthIsReadToTheEnd() =
        assertContentEquals("abc".toByteArray(), body("HTTP/1.1 200 OK\r\n\r\nabc"))

    @Test fun aMegabyteOfHeaderJunkWithoutAnEndIsRefusedQuickly() {
        val t0 = System.nanoTime()
        assertNull(readHead(input("HTTP/1.1 200 OK\r\n" + "\r".repeat(1_000_000))))
        assertTrue((System.nanoTime() - t0) / 1_000_000 < 2_000)
    }
}
