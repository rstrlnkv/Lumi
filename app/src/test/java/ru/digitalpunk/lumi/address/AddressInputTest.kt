package ru.digitalpunk.lumi.address

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AddressInputTest {
    private val g = DEFAULT_SEARCH_URL

    @Test fun bareDomainGetsHttps() = assertEquals("https://wikipedia.org", resolveInput("wikipedia.org"))

    @Test fun domainWithPathAndQueryKeepsThem() =
        assertEquals("https://example.com/a/b?x=1#top", resolveInput("example.com/a/b?x=1#top"))

    @Test fun explicitHttpIsKept() = assertEquals("http://example.com", resolveInput("http://example.com"))

    @Test fun explicitHttpsIsKept() = assertEquals("https://example.com", resolveInput("https://example.com"))

    @Test fun schemeMatchIsCaseInsensitive() = assertEquals("HTTPS://Example.com", resolveInput("HTTPS://Example.com"))

    @Test fun aboutBlankIsUnchanged() = assertEquals("about:blank", resolveInput("about:blank"))

    @Test fun localhostWithPortGetsHttp() = assertEquals("http://localhost:8080/x", resolveInput("localhost:8080/x"))

    @Test fun ipv4WithPortGetsHttp() = assertEquals("http://192.168.1.1:3000", resolveInput("192.168.1.1:3000"))

    @Test fun twoWordsAreSearchedWithPlus() = assertEquals(g + "lumi+browser", resolveInput("lumi browser"))

    @Test fun singleWordWithoutDotIsSearch() = assertEquals(g + "kotlin", resolveInput("kotlin"))

    @Test fun specialCharactersArePercentEncoded() = assertEquals(g + "c%2B%2B+%26+rust", resolveInput("c++ & rust"))

    @Test fun cyrillicQueryIsUtf8Encoded() = assertEquals(g + "%D0%BF%D1%80%D0%B8%D0%B2%D0%B5%D1%82", resolveInput("привет"))

    @Test fun hostLikeTokenWithWordIsSearch() = assertEquals(g + "example.com+login", resolveInput("example.com login"))

    @Test fun javascriptSchemeIsSearch() = assertEquals(g + "javascript%3Aalert%281%29", resolveInput("javascript:alert(1)"))

    @Test fun dataSchemeIsSearch() = assertEquals(g + "data%3Atext%2Fhtml%2Cx", resolveInput("data:text/html,x"))

    @Test fun fileSchemeIsSearch() = assertEquals(g + "file%3A%2F%2F%2Fsdcard%2Fx", resolveInput("file:///sdcard/x"))

    @Test fun blankIsNull() = assertNull(resolveInput(""))

    @Test fun whitespaceOnlyIsNull() = assertNull(resolveInput("  \t "))

    @Test fun surroundingWhitespaceIsTrimmed() = assertEquals("https://wikipedia.org", resolveInput("  wikipedia.org  "))

    @Test fun numberWithDotIsSearch() = assertEquals(g + "3.14", resolveInput("3.14"))

    @Test fun trailingDotIsSearch() = assertEquals(g + "hello.", resolveInput("hello."))
}
