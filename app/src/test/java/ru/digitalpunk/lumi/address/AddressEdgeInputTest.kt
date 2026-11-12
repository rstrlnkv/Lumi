package ru.digitalpunk.lumi.address

import kotlin.test.Test
import kotlin.test.assertEquals

/** Inputs the first test pass did not feed. */
class AddressEdgeInputTest {
    private val g = DEFAULT_SEARCH_URL

    // --- resolveInput, pinned behaviour
    @Test fun uppercaseHostGetsHttps() = assertEquals("https://WIKIPEDIA.ORG", resolveInput("WIKIPEDIA.ORG"))
    @Test fun uppercaseJavascriptSchemeIsSearch() =
        assertEquals(g + "JAVASCRIPT%3Aalert%281%29", resolveInput("JAVASCRIPT:alert(1)"))
    @Test fun uppercaseAboutIsLoadedAsTyped() = assertEquals("ABOUT:BLANK", resolveInput("ABOUT:BLANK"))
    @Test fun hostWithQueryOnlyGetsHttps() = assertEquals("https://example.com?q=1", resolveInput("example.com?q=1"))
    @Test fun hostWithFragmentOnlyGetsHttps() = assertEquals("https://example.com#top", resolveInput("example.com#top"))
    @Test fun ipv4WithoutPortGetsHttp() = assertEquals("http://10.0.0.1", resolveInput("10.0.0.1"))

    // --- resolveInput, edge cases
    @Test fun localhostWithoutPortGetsHttp() = assertEquals("http://localhost", resolveInput("localhost"))
    @Test fun ipv6LoopbackWithPort() = assertEquals("http://[::1]:8080", resolveInput("[::1]:8080"))
    @Test fun punycodeTld() = assertEquals("https://xn--80akhbyknj4f.xn--p1ai", resolveInput("xn--80akhbyknj4f.xn--p1ai"))
    @Test fun punycodeHostWithAsciiTldGetsHttps() = assertEquals("https://xn--e1afmkfd.org", resolveInput("xn--e1afmkfd.org"))
    @Test fun trailingDotFqdn() = assertEquals("https://example.com.", resolveInput("example.com."))
    @Test fun userinfoHost() = assertEquals("https://user:pass@example.com", resolveInput("user:pass@example.com"))
    @Test fun bareSchemeWithoutHostMustNotBeLoaded() = assertEquals(g + "https%3A%2F%2F", resolveInput("https://"))
    @Test fun aboutPagesOtherThanBlankMustNotBeLoadedAsTyped() =
        assertEquals(g + "about%3Aconfig", resolveInput("about:config"))

    @Test fun aboutCrashparentIsSearch() = assertEquals(g + "about%3Acrashparent", resolveInput("about:crashparent"))
    @Test fun aboutBlankWithQueryIsSearch() = assertEquals(g + "about%3Ablank%3Fx", resolveInput("about:blank?x"))
    @Test fun aboutCrashcontentIsSearchWithoutDebugFlag() =
        assertEquals(g + "about%3Acrashcontent", resolveInput("about:crashcontent"))
    @Test fun aboutCrashcontentIsLoadedWithDebugFlag() =
        assertEquals("about:crashcontent", resolveInput("about:crashcontent", debugPages = true))
    @Test fun aboutConfigIsSearchEvenWithDebugFlag() =
        assertEquals(g + "about%3Aconfig", resolveInput("about:config", debugPages = true))
    @Test fun uppercaseBareSchemeIsSearch() = assertEquals(g + "HTTPS%3A%2F%2F", resolveInput("HTTPS://"))
    @Test fun bracketedIpv6WithoutPortGetsHttp() = assertEquals("http://[::1]", resolveInput("[::1]"))

    // --- displayUrl
    @Test fun ipv6HostWithPortShowsBracketedHost() = assertEquals("[::1]", displayUrl("http://[::1]:8080/x").text)
    @Test fun ipv6HostWithoutPortKeepsBrackets() = assertEquals("[::1]", displayUrl("http://[::1]/x").text)
    @Test fun queryDirectlyAfterHost() = assertEquals("example.com", displayUrl("https://example.com?q=1").text)
    @Test fun fragmentDirectlyAfterHost() = assertEquals("example.com", displayUrl("https://example.com#a").text)
    @Test fun onlyQueryAfterSchemeIsRaw() = assertEquals(DisplayUrl("http://?q=1", false), displayUrl("http://?q=1"))
    @Test fun punycodeHostIsShownAsIs() = assertEquals("xn--e1afmkfd.org", displayUrl("https://xn--e1afmkfd.org/").text)
    @Test fun trailingDotHostIsKept() = assertEquals("example.com.", displayUrl("https://example.com./").text)
    @Test fun userPassAndPortDropped() = assertEquals("example.com", displayUrl("https://user:pass@example.com:8443/").text)
    @Test fun passwordContainingAtSignDropped() = assertEquals("example.com", displayUrl("https://u:p@ss@example.com/").text)
    @Test fun uppercaseSchemeIsSecure() = assertEquals(DisplayUrl("Example.com", true), displayUrl("HTTPS://Example.com/"))
    @Test fun uppercaseAboutBlankIsEmpty() = assertEquals(DisplayUrl("", false), displayUrl("ABOUT:BLANK"))
    @Test fun uppercaseWwwIsStripped() = assertEquals("example.com", displayUrl("https://WWW.example.com/").text)
    @Test fun editableUppercaseAboutBlankIsEmpty() = assertEquals("", editableUrl("ABOUT:BLANK"))
    @Test fun emptyUrlDoesNotCrash() = assertEquals(DisplayUrl("", false), displayUrl(""))
}
