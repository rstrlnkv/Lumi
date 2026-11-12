package ru.digitalpunk.lumi.privacy

import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.address.SiteStatus
import ru.digitalpunk.lumi.address.TrackersRow
import ru.digitalpunk.lumi.address.siteStatus
import ru.digitalpunk.lumi.address.trackersRow
import ru.digitalpunk.lumi.ui.siteIcon
import kotlin.test.Test
import kotlin.test.assertEquals

class SiteStatusPrivateTest {
    @Test fun privateHttpsShowsMaskWithCount() {
        val site = siteStatus("https://news.example/", 7, emptySet(), private = true)
        assertEquals(SiteStatus.Protected(7), site)
        assertEquals(R.drawable.ic_domino_mask, siteIcon(site, isPrivate = true))
        assertEquals(R.drawable.ic_shield, siteIcon(site, isPrivate = false))
    }

    @Test fun privateHttpKeepsTheInsecureIcon() {
        assertEquals(R.drawable.ic_no_encryption, siteIcon(siteStatus("http://a.example/", 0, emptySet(), private = true), isPrivate = true))
    }

    @Test fun privateNeverShowsProtectionOff() {
        assertEquals(SiteStatus.Protected(0), siteStatus("https://a.example/", 0, setOf("a.example"), private = true))
    }

    @Test fun httpPageShowsTheRealBlockedCountInTheSheet() {
        assertEquals(TrackersRow.Count(3), trackersRow(off = false, count = 3))
        assertEquals(TrackersRow.None, trackersRow(off = false, count = 0))
        assertEquals(TrackersRow.Off, trackersRow(off = true, count = 3))
    }
}
