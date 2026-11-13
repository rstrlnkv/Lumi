package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageEndSpacerTest {
    @Test fun aNonPositiveStripOrWidthIsNeverPosted() {
        val spacer = PageEndSpacer()
        assertNull(spacer.update(stripPx = 0, viewWidthPx = 1080))
        assertNull(spacer.update(stripPx = 168, viewWidthPx = 0))
        assertNull(spacer.update(stripPx = -1, viewWidthPx = -1))
        assertNull(spacer.current)
    }

    @Test fun theSamePairTwiceIsPostedOnce() {
        val spacer = PageEndSpacer()
        assertEquals(168 to 1080, spacer.update(168, 1080))
        assertNull(spacer.update(168, 1080))
    }

    @Test fun aWidthChangeAlonePostsAgain() {
        val spacer = PageEndSpacer()
        spacer.update(168, 1080)
        assertEquals(168 to 2340, spacer.update(168, 2340))
    }

    @Test fun anInvalidUpdateKeepsTheLastPairForAPortThatConnectsLater() {
        val spacer = PageEndSpacer()
        spacer.update(168, 1080)
        spacer.update(168, 0)
        assertEquals(168 to 1080, spacer.current)
    }

    @Test fun aStripOfOnePxAndAWidthOfOnePxAreValid() {
        assertEquals(1 to 1, PageEndSpacer().update(1, 1))
    }

    @Test fun eachOfTheTwoNumbersAloneBeingNonPositiveIsRefused() {
        assertNull(PageEndSpacer().update(stripPx = 0, viewWidthPx = 1))
        assertNull(PageEndSpacer().update(stripPx = 1, viewWidthPx = 0))
        assertNull(PageEndSpacer().update(stripPx = -1, viewWidthPx = 1))
        assertNull(PageEndSpacer().update(stripPx = 1, viewWidthPx = -1))
    }

    @Test fun aStripChangeAlonePostsAgainAndRotationBackAndForthPostsEachTime() {
        val spacer = PageEndSpacer()
        assertEquals(168 to 1080, spacer.update(168, 1080))
        assertEquals(168 to 2340, spacer.update(168, 2340))
        assertEquals(168 to 1080, spacer.update(168, 1080))
        assertEquals(144 to 1080, spacer.update(144, 1080))
    }

    @Test fun aZeroStripAfterAValidOneIsNotPostedSoTheExtensionKeepsTheOldBlock() {
        // Pinned, not endorsed: the app has no style without a strip, so 0 never arrives; if one is added this must change.
        val spacer = PageEndSpacer()
        spacer.update(168, 1080)
        assertNull(spacer.update(0, 1080))
        assertEquals(168 to 1080, spacer.current)
    }
}
