package ru.digitalpunk.lumi.data.library

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BookmarkUrlKeyTest {
    private fun same(a: String, b: String) = assertEquals(bookmarkUrlKey(a), bookmarkUrlKey(b), "$a vs $b")
    private fun differ(a: String, b: String) = assertNotEquals(bookmarkUrlKey(a), bookmarkUrlKey(b), "$a vs $b")

    @Test fun anEmptyPathAndASlashAreTheSamePage() = same("https://example.com", "https://example.com/")

    @Test fun hostAndSchemeCaseDoNotMatter() = same("HTTPS://EXAMPLE.com/a", "https://example.com/a")

    @Test fun pathCaseMatters() = differ("https://example.com/A", "https://example.com/a")

    @Test fun aTrailingDotOnTheHostIsDropped() = same("https://example.com./a", "https://example.com/a")

    @Test fun theDefaultPortIsDroppedAndAnotherPortIsKept() {
        same("https://example.com:443/", "https://example.com/")
        same("http://example.com:80/", "http://example.com/")
        differ("https://example.com:8443/", "https://example.com/")
        differ("http://example.com:443/", "http://example.com/")
    }

    @Test fun theFragmentIsDroppedAndTheQueryKept() {
        same("https://example.com/a#x", "https://example.com/a")
        differ("https://example.com/a?q=1", "https://example.com/a")
    }

    @Test fun httpAndHttpsAreDifferentPages() = differ("http://example.com/", "https://example.com/")

    @Test fun wwwIsADifferentHost() = differ("https://www.example.com/", "https://example.com/")

    @Test fun ipv6AndPunycodeHostsAreKeyedByCase() {
        same("http://[2001:DB8::1]:80/", "http://[2001:db8::1]/")
        same("https://XN--E1AFMKFD.XN--P1AI/", "https://xn--e1afmkfd.xn--p1ai")
    }

    @Test fun anUnparsableStringIsItsOwnTrimmedKey() {
        assertEquals("not a url", bookmarkUrlKey("  not a url "))
        assertEquals("https://exa mple.com", bookmarkUrlKey("https://exa mple.com"))
    }
}
