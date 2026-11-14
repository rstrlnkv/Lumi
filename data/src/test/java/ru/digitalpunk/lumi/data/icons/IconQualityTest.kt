package ru.digitalpunk.lumi.data.icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IconQualityTest {
    private fun raster(px: Int) = IconQuality(px, vector = false)
    private val svg = IconQuality(192, vector = true)

    @Test fun classBoundariesAre96ForGoodAnd180ForSharp() {
        assertEquals(IconClass.Small, iconClass(raster(95)))
        assertEquals(IconClass.Good, iconClass(raster(96)))
        assertEquals(IconClass.Good, iconClass(raster(179)))
        assertEquals(IconClass.Sharp, iconClass(raster(180)))
        assertEquals(IconClass.None, iconClass(null))
    }

    @Test fun aVectorIconRanksBetweenGoodAndSmall() {
        assertEquals(IconClass.Vector, iconClass(svg))
        assertTrue(IconClass.Vector > IconClass.Small)
        assertTrue(IconClass.Vector < IconClass.Good)
    }

    @Test fun aWorseIconNeverReplacesTheStoredOne() {
        assertFalse(shouldReplace(raster(192), raster(120)))
        assertFalse(shouldReplace(raster(120), svg))
        assertFalse(shouldReplace(svg, raster(32)))
        assertFalse(shouldReplace(raster(64), raster(32)))
    }

    @Test fun anEqualOrBetterIconReplacesTheStoredOne() {
        assertTrue(shouldReplace(raster(192), raster(192)))
        assertTrue(shouldReplace(raster(120), raster(180)))
        assertTrue(shouldReplace(raster(32), raster(57)))
        assertTrue(shouldReplace(raster(32), svg))
    }

    @Test fun anythingReplacesAnUnknownOrLegacyStoredIcon() {
        assertTrue(shouldReplace(null, raster(16)))
        assertTrue(shouldReplace(raster(0), raster(16)))
    }
}
