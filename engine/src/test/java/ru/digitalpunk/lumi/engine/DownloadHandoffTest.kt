package ru.digitalpunk.lumi.engine

import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ContentDispositionTest {
    @Test fun theStarredUtf8FormIsPercentDecoded() = assertEquals("€.pdf", parseContentDisposition("attachment; filename*=UTF-8''%E2%82%AC.pdf"))

    @Test fun aQuotedNameKeepsEscapedQuotes() = assertEquals("a \"b\".txt", parseContentDisposition("attachment; filename=\"a \\\"b\\\".txt\""))

    @Test fun aBareTokenIsRead() = assertEquals("report.pdf", parseContentDisposition("attachment; filename=report.pdf"))

    @Test fun theStarredFormWinsOverThePlainOne() =
        assertEquals("ü.txt", parseContentDisposition("attachment; filename=\"u.txt\"; filename*=UTF-8''%C3%BC.txt"))

    @Test fun anIsoLatin1StarredNameIsDecoded() = assertEquals("ü.txt", parseContentDisposition("attachment; filename*=ISO-8859-1''%FC.txt"))

    @Test fun emptyMissingOrOverlongHeadersGiveNull() {
        assertNull(parseContentDisposition("attachment; filename=\"\""))
        assertNull(parseContentDisposition(null))
        assertNull(parseContentDisposition("attachment"))
        assertNull(parseContentDisposition("attachment; filename=" + "a".repeat(10_000)))
    }

    @Test fun invalidUtf8InTheStarredFormFallsBackToThePlainName() =
        assertEquals("u.txt", parseContentDisposition("attachment; filename=u.txt; filename*=UTF-8''%FF.txt"))
}

private class Tracked(bytes: ByteArray = ByteArray(1)) : InputStream() {
    private val inner = ByteArrayInputStream(bytes)
    var closed = 0
    override fun read() = inner.read()
    override fun close() { closed++ }
}

class DownloadOfferTest {
    private fun offer(stream: InputStream) = DownloadOffer("https://x.org/a", null, null, -1, "x.org", false, stream)

    @Test fun theStreamIsHandedOutOnce() {
        val s = Tracked()
        val o = offer(s)
        assertSame(s, o.take())
        assertNull(o.take())
    }

    @Test fun discardClosesAnUntakenStreamOnce() {
        val s = Tracked()
        val o = offer(s)
        o.discard(); o.discard()
        assertEquals(1, s.closed)
    }

    @Test fun discardAfterTakeDoesNotClose() {
        val s = Tracked()
        val o = offer(s)
        o.take(); o.discard()
        assertEquals(0, s.closed)
    }

    @Test fun headersAreReadCaseInsensitively() {
        val o = downloadOffer(
            "https://cdn.x.org/f?id=1",
            mapOf("content-disposition" to "attachment; filename=a.pdf", "CONTENT-TYPE" to "application/pdf; charset=binary", "Content-Length" to "42"),
            "https://www.example.org/page", true, Tracked(),
        )!!
        assertEquals("a.pdf", o.suggestedName)
        assertEquals("application/pdf", o.mimeType)
        assertEquals(42, o.contentLength)
        assertEquals("example.org", o.site)
        assertTrue(o.inPrivateTab)
    }

    @Test fun aBadLengthIsUnknownAndTheSiteFallsBackToTheFileHost() {
        val o = downloadOffer("https://cdn.x.org/f", mapOf("Content-Length" to "lots"), "about:blank", false, Tracked())!!
        assertEquals(-1, o.contentLength)
        assertEquals("cdn.x.org", o.site)
    }

    @Test fun aBlobOrDataResponseIsRefusedAndItsStreamClosed() {
        val s = Tracked()
        assertNull(downloadOffer("blob:https://x.org/1", emptyMap(), "https://x.org", false, s))
        assertEquals(1, s.closed)
    }
}

class IsDownloadableUrlTest {
    @Test fun onlyHttpAndHttpsAreDownloadable() {
        assertTrue(isDownloadableUrl("http://x.org/a"))
        assertTrue(isDownloadableUrl("HTTPS://x.org/a"))
        listOf("blob:https://x.org/1", "data:text/plain,hi", "file:///sdcard/a", "javascript:alert(1)", "about:blank").forEach { assertFalse(isDownloadableUrl(it), it) }
    }
}

class NameFromUrlTest {
    @Test fun takesTheLastPathSegmentPercentDecoded() = assertEquals("annual report.pdf", nameFromUrl("https://x.org/files/annual%20report.pdf"))

    @Test fun dropsQueryAndFragment() = assertEquals("a.zip", nameFromUrl("https://x.org/a.zip?token=1#top"))

    @Test fun invalidUtf8IsNull() = assertNull(nameFromUrl("https://x.org/%FF%FE.bin"))

    @Test fun aTrailingSlashOrAnEmptyPathIsNull() {
        assertNull(nameFromUrl("https://x.org/dir/"))
        assertNull(nameFromUrl("https://x.org"))
    }
}

