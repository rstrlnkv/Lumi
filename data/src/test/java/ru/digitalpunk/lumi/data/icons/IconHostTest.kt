package ru.digitalpunk.lumi.data.icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IconHostTest {
    @Test fun anHttpPageMapsToTheHttpsRootOfItsHost() =
        assertEquals(IconTarget("example.com", "https://example.com/"), iconTarget("http://example.com/a/b?q=1#f"))

    @Test fun aNon443PortIsDroppedFromTheRootUrl() {
        assertEquals("https://example.com/", iconTarget("https://example.com:8443/x")?.rootUrl)
        assertEquals("https://example.com/", iconTarget("https://example.com:443/x")?.rootUrl)
        assertEquals("https://example.com/", iconTarget("http://example.com:8080/x")?.rootUrl)
    }

    @Test fun theHostIsLowerCasedWithoutTrailingDotUserInfoOrPortAndKeepsWww() {
        assertEquals("www.example.com", iconHost("https://user:pw@WWW.Example.COM.:8443/"))
    }

    @Test fun ipLiteralsLocalhostSingleLabelAndLocalNamesGiveNull() {
        listOf(
            "https://192.168.0.1/", "https://[::1]/", "https://[2001:db8::1]:8443/", "https://localhost/",
            "https://router/", "https://printer.local/", "https://a.localhost/", "https://db.internal/",
        ).forEach { assertNull(iconTarget(it), it) }
    }

    @Test fun otherSchemesBlankAndGarbageGiveNull() {
        listOf("", "   ", "about:blank", "file:///sdcard/x.html", "ftp://example.com/", "javascript:alert(1)", "http://", "not a url")
            .forEach { assertNull(iconTarget(it), it) }
    }

    @Test fun httpAndHttpsOfTheSameHostShareOneHost() = assertEquals(iconHost("http://a.com"), iconHost("https://a.com"))

    @Test fun limitsArePinned() {
        assertEquals(1_048_576, MAX_BYTES)
        assertEquals(30_000L, TOTAL_TIMEOUT_MS)
        assertEquals(192, ICON_SIZE)
    }
}
