package ru.digitalpunk.lumi.gesture

import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertEquals

class BarGeometryTest {
    @Test fun maxHiddenIsExpandedMinusCollapsed() = assertEquals(150f, BarGeometry.maxHidden(expanded = 200f, collapsed = 50f))

    @Test fun maxHiddenNeverNegative() = assertEquals(0f, BarGeometry.maxHidden(expanded = 40f, collapsed = 50f))

    @Test fun clippingIsZeroWhenNothingIsHidden() = assertEquals(0f, BarGeometry.clipping(hidden = 0f))

    @Test fun clippingIsMinusTheSlidingPartWhenFullyHidden() = assertEquals(-150f, BarGeometry.clipping(hidden = 150f))

    @Test fun progressIsFractionHidden() = assertEquals(0.5f, BarGeometry.progress(hidden = 75f, maxHidden = 150f))

    @Test fun progressIsOneWhenThereIsNothingToHide() = assertEquals(1f, BarGeometry.progress(hidden = 0f, maxHidden = 0f))

    // 1000 px screen, 300 px bar (nav inset 40 + collapsed layer 100 + rest), collapsed leaves 140 px.
    @Test fun collapsedStripLayerEndsExactlyAtTheNavInset() {
        val screen = 1000f
        val navInset = 40f
        val layer = 100f
        val expanded = 300f
        val maxHidden = BarGeometry.maxHidden(expanded, collapsed = layer + navInset)
        val top = BarGeometry.stripTop(screen, expanded, hidden = maxHidden)
        assertEquals(screen - navInset - layer, top)
        assertEquals(screen - navInset, top + layer)
    }

    // Gecko takes whole px. A fractional strip (48 dp at density 3.1375 is 150.6 px, plus a 50 px inset) put the band at
    // 2571.4 and a site's `bottom:0` bar at 2571: one row of page showed between them (netflix.com).
    @Test fun aCollapsedBarRestsOnAWholePixelSoTheBandMeetsGeckosFixedBottom() {
        val screen = 2772f
        val expanded = 300f
        val hidden = BarGeometry.maxHidden(expanded, collapsed = 200.6f)
        val geckoFixedBottom = screen - expanded - BarGeometry.clipping(hidden).roundToInt()
        assertEquals(geckoFixedBottom, BarGeometry.stripTop(screen, expanded, hidden))
    }

    @Test fun stripTopIsTheBarTopWhenExpanded() = assertEquals(700f, BarGeometry.stripTop(1000f, 300f, 0f))

    // The Compact band fills the bar box: expanded, its top is where Gecko ends `bottom:0`; collapsed, only the strip's zone is left.
    @Test fun theBandTopIsTheViewportBottomWhenExpandedAndTheStripZoneWhenCollapsed() {
        val screen = 1000f
        val expanded = 300f
        val collapsed = 140f
        assertEquals(screen - expanded, BarGeometry.stripTop(screen, expanded, hidden = 0f))
        assertEquals(screen - collapsed, BarGeometry.stripTop(screen, expanded, hidden = BarGeometry.maxHidden(expanded, collapsed)))
    }
}
