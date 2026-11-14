package ru.digitalpunk.lumi.ui

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The site colour rules: WCAG luminance and contrast pick the status and navigation icon colour; the band falls back. */
class SiteColorRulesTest {
    private fun gray(v: Int) = (0xFF shl 24) or (v shl 16) or (v shl 8) or v

    @Test fun theLuminanceOfBlackWhiteAndAKnownGray() {
        assertEquals(0.0, relativeLuminance(0xFF000000.toInt()), 1e-9)
        assertEquals(1.0, relativeLuminance(0xFFFFFFFF.toInt()), 1e-9)
        assertEquals(0.2159, relativeLuminance(gray(0x80)), 1e-4)
    }

    @Test fun theContrastOfBlackAndWhiteIs21AndDoesNotDependOnTheOrder() {
        assertEquals(21.0, contrast(1.0, 0.0), 1e-9)
        assertEquals(contrast(0.2, 0.7), contrast(0.7, 0.2), 1e-12)
    }

    @Test fun iconsTakeTheColourWithTheHigherContrast() {
        assertTrue(darkIconsOn(0xFFFFFFFF.toInt()), "white")
        assertFalse(darkIconsOn(0xFF000000.toInt()), "black")
        assertTrue(darkIconsOn(0xFFFFFF00.toInt()), "yellow")
        assertFalse(darkIconsOn(0xFF0B57D0.toInt()), "#0B57D0")
        assertTrue(darkIconsOn(0xFF777777.toInt()), "#777777")
    }

    // The contrasts with black and white are equal at L = sqrt(1.05 * 0.05) - 0.05 = 0.1791; 0x75 is 0.1779, 0x76 0.1812.
    @Test fun theBoundaryLiesBetweenGrays0x75And0x76() {
        assertEquals(0.1779, relativeLuminance(gray(0x75)), 1e-4)
        assertEquals(0.1812, relativeLuminance(gray(0x76)), 1e-4)
        assertFalse(darkIconsOn(gray(0x75)))
        assertTrue(darkIconsOn(gray(0x76)))
    }

    @Test fun alphaDoesNotChangeTheChoice() =
        assertEquals(darkIconsOn(0xFF0B57D0.toInt()), darkIconsOn(0x000B57D0))

    @Test fun theBandTakesTheSiteColourElseTheSurface() {
        val surface = Color(0xFF123456)
        assertEquals(surface, bandColor(null, surface))
        assertEquals(Color(0xFF0B57D0), bandColor(0xFF0B57D0.toInt(), surface))
    }
}
