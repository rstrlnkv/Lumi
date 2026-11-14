package ru.digitalpunk.lumi.engine

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ru.digitalpunk.lumi.engine.AddressSpace.Local
import ru.digitalpunk.lumi.engine.AddressSpace.Private
import ru.digitalpunk.lumi.engine.AddressSpace.Public

/** Chrome's address table (ADR 015) for Lumi's own fetches: each range at its first and last address and one step outside. */
class NetAddressTest {
    private fun ip(text: String): ByteArray = InetAddress.getByName(text).address

    // getByName turns "::ffff:a.b.c.d" into a 4-byte address, so mapped addresses are built by hand.
    private fun mapped(v4: String) = ByteArray(10) + byteArrayOf(-1, -1) + ip(v4)

    private val table = listOf(
        "0.0.0.0" to Local, "0.255.255.255" to Local, "1.0.0.0" to Public,
        "9.255.255.255" to Public, "10.0.0.0" to Private, "10.255.255.255" to Private, "11.0.0.0" to Public,
        "100.63.255.255" to Public, "100.64.0.0" to Private, "100.127.255.255" to Private, "100.128.0.0" to Public,
        "126.255.255.255" to Public, "127.0.0.0" to Local, "127.255.255.255" to Local, "128.0.0.0" to Public,
        "169.253.255.255" to Public, "169.254.0.0" to Private, "169.254.255.255" to Private, "169.255.0.0" to Public,
        "172.15.255.255" to Public, "172.16.0.0" to Private, "172.31.255.255" to Private, "172.32.0.0" to Public,
        "192.167.255.255" to Public, "192.168.0.0" to Private, "192.168.255.255" to Private, "192.169.0.0" to Public,
        "198.17.255.255" to Public, "198.18.0.0" to Public, "198.19.255.255" to Public, "198.20.0.0" to Public,
        "239.255.255.255" to Public, "240.0.0.0" to Public, "255.255.255.255" to Public,
        "8.8.8.8" to Public, "1.1.1.1" to Public,
        "::1" to Local, "::" to Local, "::2" to Public,
        "fbff:ffff:ffff:ffff:ffff:ffff:ffff:ffff" to Public, "fc00::" to Private,
        "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff" to Private, "fe00::" to Public,
        "fe7f:ffff:ffff:ffff:ffff:ffff:ffff:ffff" to Public, "fe80::" to Private,
        "febf:ffff:ffff:ffff:ffff:ffff:ffff:ffff" to Private, "fec0::" to Public,
        "2001:db8::1" to Public, "2606:4700::1111" to Public, "64:ff9b::a00:1" to Public,
    )

    @Test fun everyBoundaryOfTheTableIsClassifiedLikeChrome() {
        table.forEach { (text, space) -> assertEquals(space, addressSpace(ip(text)), text) }
    }

    @Test fun anIpv4MappedAddressIsJudgedByItsIpv4Part() {
        assertEquals(Local, addressSpace(mapped("127.0.0.1")))
        assertEquals(Private, addressSpace(mapped("10.0.0.1")))
        assertEquals(Private, addressSpace(mapped("192.168.1.1")))
        assertEquals(Public, addressSpace(mapped("8.8.8.8")))
        assertEquals(Public, addressSpace(mapped("240.0.0.5")))
    }

    @Test fun anAddressOfAnyOtherLengthHasNoSpaceAndDoesNotThrow() {
        assertNull(addressSpace(ByteArray(0)))
        assertNull(addressSpace(ByteArray(5)))
        assertNull(addressSpace(ByteArray(17)))
    }

    @Test fun oneLocalOrPrivateAddressRefusesTheFetchEvenNextToAPublicOne() {
        assertTrue(refuseFetch(listOf(ip("93.184.216.34"), ip("192.168.0.10"))))
        assertTrue(refuseFetch(listOf(ip("2606:4700::1111"), ip("::1"))))
        assertTrue(refuseFetch(listOf(ip("8.8.8.8"), ByteArray(5))))
    }

    @Test fun noAddressAtAllRefusesTheFetch() = assertTrue(refuseFetch(emptyList()))

    @Test fun theVpnFakeIpRangesStayPublicSoIconsAndSuggestionsStillLoad() {
        assertFalse(refuseFetch(listOf(ip("240.0.0.5"))))
        assertFalse(refuseFetch(listOf(ip("198.18.0.7"), ip("2001:db8::1"))))
    }

    @Test fun onlyHttpsUrlsWithAHostNameAreFetched() {
        assertEquals("example.com", fetchableHost("https://example.com/favicon.ico"))
        assertEquals("example.com", fetchableHost("HTTPS://example.com"))
        assertNull(fetchableHost("http://example.com/"))
        assertNull(fetchableHost("ftp://example.com/"))
        assertNull(fetchableHost("https:///path"))
        assertNull(fetchableHost("not a url"))
    }

    @Test fun anIpLiteralInAnyFormIsRefusedBeforeResolving() {
        listOf(
            "https://192.168.0.1/", "https://8.8.8.8/", "https://[::1]/", "https://[2606:4700::1111]/",
            "https://2130706433/", "https://0x7f000001/", "https://127.1/", "https://10.0.0.1./",
        ).forEach { assertNull(fetchableHost(it), it) }
    }

    // Java's URL and Gecko's parser can read one string as two hosts: only a plain ASCII authority is fetched.
    @Test fun aHostGeckoMightReadDifferentlyIsRefused() {
        listOf(
            "https://192.168.1.1\\@example.com/", "https://192.168.1.1\\.example.com/", "https://exa\tmple.com/",
            "https://example.com\n/", " https://example.com/", "https://１９２．１６８．１．１/", "https://exa\u200Bmple.com/",
            "https://user:pw@example.com/", "https://user@example.com/", "https://host%41.com/", "https://ex%41mple.com/",
            "https://example.com/a\tb", "https://example.com/a\u0000b", "https://example.com/a\\b",
        ).forEach { assertNull(fetchableHost(it), it) }
        assertEquals("example.com", fetchableHost("https://example.com:8443/a%20b?q=%31#x"))
        assertEquals("xn--d1acufc.xn--p1ai", fetchableHost("https://xn--d1acufc.xn--p1ai/"))
    }

    @Test fun aRedirectToUserInfoOrAPercentSignInTheHostIsRefused() {
        listOf("https://user@host.com/", "https://user:pw@host.com/", "https://host%41.com/")
            .forEach { assertNull(nextHop("https://example.com/", it), it) }
    }

    @Test fun aRedirectIsResolvedAgainstTheCurrentUrl() {
        assertEquals("https://example.com/b/c", nextHop("https://example.com/a/x", "/b/c"))
        assertEquals("https://example.com/a/y", nextHop("https://example.com/a/x", "y"))
        assertEquals("https://cdn.example.org/i.png", nextHop("https://example.com/a", "https://cdn.example.org/i.png"))
        assertEquals("https://cdn.example.org/i.png", nextHop("https://example.com/a", "//cdn.example.org/i.png"))
    }

    @Test fun aRedirectToHttpAnIpLiteralOrNowhereIsRefused() {
        assertNull(nextHop("https://example.com/", "http://example.com/"))
        assertNull(nextHop("https://example.com/", "https://192.168.1.1/admin"))
        assertNull(nextHop("https://example.com/", "https://[fe80::1]/"))
        assertNull(nextHop("https://example.com/", null))
        assertNull(nextHop("https://example.com/", ""))
        assertNull(nextHop("https://example.com/", "javascript:alert(1)"))
    }

    @Test fun theRedirectCodesAndHopLimitAreChromes() {
        assertEquals(setOf(301, 302, 303, 307, 308), REDIRECT_CODES)
        assertEquals(5, MAX_REDIRECTS)
    }
}
