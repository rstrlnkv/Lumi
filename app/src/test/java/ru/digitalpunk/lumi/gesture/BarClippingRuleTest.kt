package ru.digitalpunk.lumi.gesture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The clipping rule fed through the real bar state, plus degenerate sizes. Literal numbers on purpose. */
class BarClippingRuleTest {
    private fun clip(expanded: Float, collapsed: Float, bar: BarCollapse) =
        BarGeometry.clipping(expanded, collapsed, bar.hidden)

    @Test fun halfwayHiddenLeavesHalfOfTheSlidingPartCovering() =
        assertEquals(60f, BarGeometry.clipping(expanded = 200f, collapsed = 80f, hidden = 60f))

    @Test fun clippingIsLinearInHiddenWithSlopeMinusOne() {
        assertEquals(100f, BarGeometry.clipping(300f, 100f, 100f))
        assertEquals(199f, BarGeometry.clipping(300f, 100f, 1f))
    }

    @Test fun collapsedEqualToExpandedGivesZeroAtEveryHidden() {
        assertEquals(0f, BarGeometry.clipping(150f, 150f, 0f))
        assertEquals(0f, BarGeometry.maxHidden(150f, 150f))
    }

    @Test fun barHeightZeroGivesZero() {
        assertEquals(0f, BarGeometry.clipping(0f, 0f, 0f))
    }

    @Test fun expandedZeroWithStripGivesZero() {
        assertEquals(0f, BarGeometry.clipping(0f, 50f, 0f))
    }

    @Test fun draggedFarPastTheEndNeverGivesGeckoANegativeClipping() {
        val bar = BarCollapse(150f)
        bar.drag(-100000f)
        assertEquals(0f, clip(200f, 50f, bar))
        bar.drag(100000f)
        assertEquals(150f, clip(200f, 50f, bar))
    }

    @Test fun shrinkingTheMaxWhileHiddenKeepsClippingInRange() {
        val bar = BarCollapse(150f)
        bar.snapTo(150f)
        bar.setMax(40f)          // e.g. rotation: the bar got shorter
        val c = clip(90f, 50f, bar)
        assertEquals(0f, c)
        bar.setMax(-5f)
        assertEquals(0f, bar.hidden)
        assertTrue(clip(40f, 50f, bar) == 0f)
    }

    @Test fun negativeHiddenOutsideTheStateRaisesClippingAboveTheSlidingPart() {
        // Documents that the pure rule does not clamp: the bar state must (and does) keep hidden in range.
        assertEquals(170f, BarGeometry.clipping(200f, 50f, -20f))
    }
}
