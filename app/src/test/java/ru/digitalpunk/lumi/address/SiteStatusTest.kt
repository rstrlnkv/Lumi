package ru.digitalpunk.lumi.address

import kotlin.test.Test
import kotlin.test.assertEquals

class SiteStatusTest {
    @Test fun httpsPageWithBlockedTrackersGivesProtectedWithTheCount() =
        assertEquals(SiteStatus.Protected(7), siteStatus("https://news.example/a", 7, emptySet()))

    @Test fun httpsPageWithZeroBlockedGivesProtectedZero() =
        assertEquals(SiteStatus.Protected(0), siteStatus("https://news.example/", 0, emptySet()))

    @Test fun httpsHostInTheOffSetGivesProtectionOff() =
        assertEquals(SiteStatus.ProtectionOff, siteStatus("https://news.example/", 3, setOf("news.example")))

    @Test fun wwwAndNonWwwMatchTheSameOffHost() =
        assertEquals(SiteStatus.ProtectionOff, siteStatus("https://WWW.News.example/", 0, setOf("news.example")))

    @Test fun httpPageGivesInsecure() = assertEquals(SiteStatus.Insecure, siteStatus("http://news.example/", 2, emptySet()))

    @Test fun httpHostInTheOffSetStillGivesInsecure() =
        assertEquals(SiteStatus.Insecure, siteStatus("http://news.example/", 0, setOf("news.example")))

    @Test fun aboutBlankGivesNone() = assertEquals(SiteStatus.None, siteStatus("about:blank", 0, emptySet()))

    @Test fun nonWebSchemeGivesNone() = assertEquals(SiteStatus.None, siteStatus("about:support", 0, emptySet()))
}

class CountLabelTest {
    @Test fun zeroGivesEmpty() = assertEquals("", countLabel(0))

    @Test fun ninetyNineGives99() = assertEquals("99", countLabel(99))

    @Test fun oneHundredGives99Plus() = assertEquals("99+", countLabel(100))

    @Test fun overAThousandGives99Plus() = assertEquals("99+", countLabel(1234))
}
