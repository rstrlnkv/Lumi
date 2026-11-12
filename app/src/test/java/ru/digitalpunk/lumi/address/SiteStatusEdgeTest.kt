package ru.digitalpunk.lumi.address

import kotlin.test.Test
import kotlin.test.assertEquals

class SiteStatusEdgeTest {
    @Test fun exceptionForABareDomainDoesNotProtectOffItsSubdomain() =
        assertEquals(SiteStatus.Protected(1), siteStatus("https://sub.example.com/", 1, setOf("example.com")))

    @Test fun uppercaseHostMatchesALowerCaseException() =
        assertEquals(SiteStatus.ProtectionOff, siteStatus("https://EXAMPLE.COM/", 0, setOf("example.com")))

    @Test fun trailingDotHostMatchesTheExceptionForTheBareHost() =
        assertEquals(SiteStatus.ProtectionOff, siteStatus("https://example.com./", 0, setOf("example.com")))

    @Test fun portDoesNotChangeTheMatch() =
        assertEquals(SiteStatus.ProtectionOff, siteStatus("https://example.com:8443/", 0, setOf("example.com")))

    @Test fun ipv6HttpsPageGivesNoStatus() = assertEquals(SiteStatus.None, siteStatus("https://[::1]:8080/", 0, emptySet()))

    @Test fun ipv4AndLocalhostAreMatchedByTheirText() {
        assertEquals(SiteStatus.ProtectionOff, siteStatus("https://192.168.0.1/", 0, setOf("192.168.0.1")))
        assertEquals(SiteStatus.Insecure, siteStatus("http://localhost:3000/", 0, setOf("localhost")))
    }

    @Test fun uppercaseSchemeHttpsIsStillProtectedNotInsecure() =
        assertEquals(SiteStatus.Protected(2), siteStatus("HTTPS://example.com/", 2, emptySet()))

    @Test fun uppercaseSchemeHttpIsInsecure() = assertEquals(SiteStatus.Insecure, siteStatus("HTTP://example.com/", 2, emptySet()))

    @Test fun emptyUrlGivesNone() = assertEquals(SiteStatus.None, siteStatus("", 5, setOf("")))

    @Test fun protectedKeepsACountAbove999Unclamped() = assertEquals(SiteStatus.Protected(5000), siteStatus("https://a.com/", 5000, emptySet()))

    @Test fun switchingOffThenOnFlipsTheStatusBack() {
        val url = "https://a.com/"
        assertEquals(SiteStatus.ProtectionOff, siteStatus(url, 0, setOf("a.com")))
        assertEquals(SiteStatus.Protected(0), siteStatus(url, 0, emptySet()))
    }
}

class CountLabelEdgeTest {
    @Test fun negativeGivesEmpty() = assertEquals("", countLabel(-5))

    @Test fun oneGivesOne() = assertEquals("1", countLabel(1))

    @Test fun boundaryNinetyNineAndHundred() {
        assertEquals("99", countLabel(99))
        assertEquals("99+", countLabel(100))
        assertEquals("99+", countLabel(101))
    }

    @Test fun values999To1000AndHugeGive99Plus() {
        assertEquals("99+", countLabel(999))
        assertEquals("99+", countLabel(1000))
        assertEquals("99+", countLabel(Int.MAX_VALUE))
    }

    @Test fun intMinGivesEmpty() = assertEquals("", countLabel(Int.MIN_VALUE))
}
