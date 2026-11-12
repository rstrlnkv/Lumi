package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SiteKeyTest {
    @Test fun hostIsLowerCased() = assertEquals("example.com", siteKey("https://Example.COM/path"))

    @Test fun leadingWwwIsRemoved() = assertEquals("example.com", siteKey("https://WWW.example.com/"))

    @Test fun portIsRemoved() = assertEquals("example.com", siteKey("http://example.com:8080/a?b#c"))

    @Test fun userinfoIsRemoved() = assertEquals("example.com", siteKey("https://user:pw@example.com/"))

    @Test fun aboutBlankGivesNull() = assertNull(siteKey("about:blank"))

    @Test fun nonWebSchemeGivesNull() {
        assertNull(siteKey("file:///sdcard/a.html"))
        assertNull(siteKey("data:text/html,hi"))
    }

    @Test fun ipv6AndEmptyHostUrlsGiveNull() {
        assertNull(siteKey("https://[::1]:8080/"))
        assertNull(siteKey("https:///path"))
        assertNull(siteKey("https://"))
    }
}
