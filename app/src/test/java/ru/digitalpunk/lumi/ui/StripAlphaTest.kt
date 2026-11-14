package ru.digitalpunk.lumi.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StripAlphaTest {
    @Test fun controlsAndStripNeverShowTogether() {
        for (i in 0..100) {
            val p = i / 100f
            assertEquals(0f, minOf(controlsAlpha(p), stripAlpha(p)))
        }
    }

    @Test fun endsOfTheSlideShowOneLayerFully() {
        assertEquals(1f, controlsAlpha(0f))
        assertEquals(0f, stripAlpha(0f))
        assertEquals(0f, controlsAlpha(1f))
        assertEquals(1f, stripAlpha(1f))
    }

    @Test fun theBandTakesTouchesUnlessTheBarIsFullyCollapsed() {
        assertTrue(bandTakesTouches(collapsed = false))
        assertFalse(bandTakesTouches(collapsed = true))
    }
}
