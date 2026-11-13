package ru.digitalpunk.lumi.data.icons

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IconPlanTest {
    /** Edge to edge: the whole square is the destination. */
    private fun IconPlan.fills() = dstX == 0 && dstY == 0 && dstW == ICON_SIZE && dstH == ICON_SIZE

    @Test fun anOpaqueIconOfAtLeast96FillsTheTile() {
        assertTrue(assertNotNull(planIcon(96, 96, opaque = true)).fills())
        assertTrue(assertNotNull(planIcon(512, 512, opaque = true)).fills())
    }

    @Test fun under96OrNotOpaqueIsSmall() {
        assertFalse(assertNotNull(planIcon(95, 96, opaque = true)).fills())
        assertFalse(assertNotNull(planIcon(180, 180, opaque = false)).fills())
        assertFalse(assertNotNull(planIcon(16, 16, opaque = true)).fills())
    }

    @Test fun under16IsNoIcon() = assertNull(planIcon(15, 15, opaque = true))

    @Test fun aWideOpaqueIconIsCentreCroppedToASquare() {
        val plan = assertNotNull(planIcon(200, 100, opaque = true))
        assertEquals(IconPlan(50, 0, 100, 100, 0, 0, 96, 96), plan)
    }

    @Test fun aSmallRectangleIsAlwaysInsetAtLeastFourAndKeepsTheAspect() {
        listOf(16 to 16, 95 to 96, 300 to 100, 100 to 300, 180 to 180).forEach { (w, h) ->
            val p = assertNotNull(planIcon(w, h, opaque = false))
            assertTrue(p.dstX >= 4 && p.dstY >= 4 && p.dstX + p.dstW <= 92 && p.dstY + p.dstH <= 92, "$w x $h -> $p")
            assertTrue(p.dstW == 88 || p.dstH == 88, "$w x $h fits 88 -> $p")
        }
        assertEquals(IconPlan(0, 0, 300, 100, 4, 33, 88, 29), planIcon(300, 100, opaque = false))
    }

    @Test fun fillsTileOnlyWhenEveryPixelIsFullyOpaque() {
        assertTrue(fillsTile(IntArray(9) { 0xFF336699.toInt() }))
        assertFalse(fillsTile(IntArray(9) { 0xFF336699.toInt() }.also { it[4] = 0xFE336699.toInt() }))
    }

    @Test fun aWhiteEdgeIsLightAndAColouredEdgeIsNot() {
        assertTrue(hasLightEdge(IntArray(16) { 0xFFFFFFFF.toInt() }.also { it[5] = 0xFF000000.toInt() }, 4))
        assertFalse(hasLightEdge(IntArray(16) { 0xFF3366CC.toInt() }, 4))
    }

    @Test fun anIconIsFreshUntilThirtyDaysAndStaleFromThen() {
        val now = 100L * 24 * 3_600_000
        assertFalse(isStale(now - 2_592_000_000L + 1, now))
        assertTrue(isStale(now - 2_592_000_000L, now))
    }

    @Test fun aModificationTimeMoreThanADayAheadIsStale() {
        val now = 100L * 24 * 3_600_000
        assertFalse(isStale(now + 86_400_000L, now))
        assertTrue(isStale(now + 86_400_000L + 1, now))
    }
}
