package ru.digitalpunk.lumi.gesture

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

    @Test fun stripTopIsTheBarTopWhenExpanded() = assertEquals(700f, BarGeometry.stripTop(1000f, 300f, 0f))
}
