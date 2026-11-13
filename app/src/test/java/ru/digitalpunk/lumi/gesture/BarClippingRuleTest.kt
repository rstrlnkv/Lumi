package ru.digitalpunk.lumi.gesture

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The clipping rule fed through the real bar state. Gecko gets the whole bar as its maximum and the slid-away
 * part as a negative clipping (seen on a device: `fixed; bottom:0` then ends at the bar, expanded or collapsed).
 */
class BarClippingRuleTest {
    @Test fun anExpandedBarGivesNoClipping() = assertEquals(0f, BarGeometry.clipping(hidden = 0f))

    @Test fun theSlidAwayPartIsGivenAsANegativeClipping() = assertEquals(-60f, BarGeometry.clipping(hidden = 60f))

    @Test fun draggedFarPastTheEndKeepsClippingBetweenZeroAndMinusTheSlidingPart() {
        val bar = BarCollapse(BarGeometry.maxHidden(200f, 50f))
        bar.drag(-100000f)       // finger up: the bar collapses
        assertEquals(-150f, BarGeometry.clipping(bar.hidden))
        bar.drag(100000f)
        assertEquals(0f, BarGeometry.clipping(bar.hidden))
    }

    @Test fun shrinkingTheMaxWhileHiddenShrinksTheClippingWithIt() {
        val bar = BarCollapse(150f)
        bar.snapTo(150f)
        bar.setMax(40f)          // e.g. rotation: the bar got shorter
        assertEquals(-40f, BarGeometry.clipping(bar.hidden))
        bar.setMax(-5f)
        assertEquals(0f, BarGeometry.clipping(bar.hidden))
    }
}
