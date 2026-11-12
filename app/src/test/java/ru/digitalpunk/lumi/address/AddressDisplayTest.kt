package ru.digitalpunk.lumi.address

import kotlin.test.Test
import kotlin.test.assertEquals

class AddressDisplayTest {
    @Test fun httpsShowsHostAndIsSecure() = assertEquals(DisplayUrl("example.com", true), displayUrl("https://example.com/a"))

    @Test fun httpShowsHostAndIsNotSecure() = assertEquals(DisplayUrl("example.com", false), displayUrl("http://example.com"))

    @Test fun wwwIsStripped() = assertEquals("example.com", displayUrl("https://www.example.com/").text)

    @Test fun portPathQueryFragmentAreDropped() {
        assertEquals("example.com", displayUrl("https://example.com:8443/a/b?q=1#x").text)
        assertEquals("example.com", displayUrl("https://example.com?q=1").text)
        assertEquals("example.com", displayUrl("https://example.com#x").text)
    }

    @Test fun userinfoIsDropped() = assertEquals("example.com", displayUrl("https://user:pw@example.com/").text)

    @Test fun aboutBlankIsEmpty() = assertEquals(DisplayUrl("", false), displayUrl("about:blank"))

    @Test fun otherAboutPagesAreRaw() = assertEquals(DisplayUrl("about:crashcontent", false), displayUrl("about:crashcontent"))

    @Test fun unicodeHostIsKept() = assertEquals("пример.рф", displayUrl("https://пример.рф/путь").text)

    @Test fun malformedIsRawAndNotSecure() {
        assertEquals(DisplayUrl("not a url", false), displayUrl("not a url"))
        assertEquals(DisplayUrl("", false), displayUrl(""))
        assertEquals(DisplayUrl("https://", false), displayUrl("https://"))
    }

    @Test fun editableIsEmptyForAboutBlank() = assertEquals("", editableUrl("about:blank"))

    @Test fun editableIsFullUrlOtherwise() = assertEquals("https://example.com/a?b=1", editableUrl("https://example.com/a?b=1"))
}
