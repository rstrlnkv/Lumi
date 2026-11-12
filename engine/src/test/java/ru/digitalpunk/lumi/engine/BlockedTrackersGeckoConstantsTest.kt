package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.ContentBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [countsAsTracker] treats 0 as "no category"; these tests read the real GeckoView constants so a renumbering shows. */
class BlockedTrackersGeckoConstantsTest {
    @Test fun everyAntiTrackingFlagCountsAndNoneDoesNot() {
        listOf(
            ContentBlocking.AntiTracking.AD,
            ContentBlocking.AntiTracking.ANALYTIC,
            ContentBlocking.AntiTracking.SOCIAL,
            ContentBlocking.AntiTracking.CONTENT,
            ContentBlocking.AntiTracking.CRYPTOMINING,
            ContentBlocking.AntiTracking.FINGERPRINTING,
            ContentBlocking.AntiTracking.STP,
            ContentBlocking.AntiTracking.EMAIL,
        ).forEach { assertTrue(countsAsTracker(it, 0, blocking = true), "flag $it") }
        assertFalse(countsAsTracker(ContentBlocking.AntiTracking.NONE, ContentBlocking.CookieBehavior.ACCEPT_ALL, blocking = true))
    }

    @Test fun safeBrowsingCategoriesAloneDoNotCount() {
        listOf(
            ContentBlocking.SafeBrowsing.MALWARE,
            ContentBlocking.SafeBrowsing.UNWANTED,
            ContentBlocking.SafeBrowsing.HARMFUL,
            ContentBlocking.SafeBrowsing.PHISHING,
        ).forEach { assertFalse(countsAsTracker(ContentBlocking.AntiTracking.NONE, ContentBlocking.CookieBehavior.ACCEPT_ALL, blocking = true), "flag $it") }
    }

    @Test fun aBlockedThirdPartyCookieCounts() =
        assertTrue(countsAsTracker(ContentBlocking.AntiTracking.NONE, ContentBlocking.CookieBehavior.ACCEPT_NON_TRACKERS, blocking = true))
}
