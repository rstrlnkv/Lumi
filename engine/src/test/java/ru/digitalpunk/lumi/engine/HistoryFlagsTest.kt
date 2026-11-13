package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoSession.HistoryDelegate.VISIT_REDIRECT_PERMANENT
import org.mozilla.geckoview.GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE
import org.mozilla.geckoview.GeckoSession.HistoryDelegate.VISIT_REDIRECT_SOURCE_PERMANENT
import org.mozilla.geckoview.GeckoSession.HistoryDelegate.VISIT_REDIRECT_TEMPORARY
import org.mozilla.geckoview.GeckoSession.HistoryDelegate.VISIT_TOP_LEVEL
import org.mozilla.geckoview.GeckoSession.HistoryDelegate.VISIT_UNRECOVERABLE_ERROR
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Reads GeckoView's real constants, so a renumbering cannot hide behind copied numbers. */
class HistoryFlagsTest {
    @Test fun aTopLevelVisitIsReportedAlsoWhenItIsTheTargetOfARedirect() {
        assertTrue(shouldReport(isPrivate = false, VISIT_TOP_LEVEL))
        assertTrue(shouldReport(isPrivate = false, VISIT_TOP_LEVEL or VISIT_REDIRECT_TEMPORARY))
        assertTrue(shouldReport(isPrivate = false, VISIT_TOP_LEVEL or VISIT_REDIRECT_PERMANENT))
    }

    @Test fun aFrameVisitIsNotReported() {
        assertFalse(shouldReport(isPrivate = false, 0))
        assertFalse(shouldReport(isPrivate = false, VISIT_REDIRECT_TEMPORARY))
    }

    @Test fun aRedirectSourceOrAnErrorIsNotReportedEvenAtTopLevel() {
        listOf(VISIT_REDIRECT_SOURCE, VISIT_REDIRECT_SOURCE_PERMANENT, VISIT_UNRECOVERABLE_ERROR).forEach {
            assertFalse(shouldReport(isPrivate = false, VISIT_TOP_LEVEL or it), "flag $it")
        }
    }

    @Test fun aPrivateVisitIsNeverReportedWhateverTheFlags() {
        (0..63).forEach { assertFalse(shouldReport(isPrivate = true, it), "flags $it") }
    }
}
