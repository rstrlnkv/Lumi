package ru.digitalpunk.lumi.gesture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Boundaries and degenerate sizes the main BarCollapse tests do not feed. */
class BarCollapseEdgeTest {
    @Test fun settleLeavesBarExpandedJustUnderTwentyPercentUp() {
        val bar = BarCollapse(100f)
        bar.drag(-15f)
        assertEquals(0f, bar.settleTarget())
    }

    @Test fun settleKeepsBarCollapsedJustUnderTwentyPercentDown() {
        val bar = BarCollapse(100f)
        bar.snapTo(bar.maxHidden)
        bar.drag(15f)
        assertEquals(100f, bar.settleTarget())
    }

    @Test fun zeroDragKeepsTheLastDirection() {
        val bar = BarCollapse(100f)
        bar.drag(-50f)
        bar.drag(0f)
        assertEquals(100f, bar.settleTarget())
    }

    @Test fun zeroDragOnFreshBarDoesNotMoveIt() {
        val bar = BarCollapse(100f)
        bar.drag(0f)
        assertTrue(bar.isExpanded)
        assertEquals(0f, bar.settleTarget())
    }

    @Test fun zeroMaxBarCannotMoveAndSettlesExpanded() {
        val bar = BarCollapse(0f)
        bar.drag(-80f)
        assertEquals(0f, bar.hidden)
        assertEquals(0f, bar.settleTarget())
        bar.setMax(0f)
        assertEquals(0f, bar.hidden)
    }

    @Test fun shrinkingMaxToZeroExpandsTheBar() {
        val bar = BarCollapse(100f)
        bar.snapTo(bar.maxHidden)
        bar.setMax(0f)
        assertEquals(0f, bar.hidden)
    }

    @Test fun growingMaxKeepsHiddenWhereItWas() {
        val bar = BarCollapse(50f)
        bar.snapTo(bar.maxHidden)
        bar.setMax(100f)
        assertEquals(50f, bar.hidden)
        assertFalse(bar.isCollapsed)
    }

    @Test fun hugeDragsStayInsideTheRange() {
        val bar = BarCollapse(100f)
        bar.drag(-Float.MAX_VALUE)
        assertEquals(100f, bar.hidden)
        bar.drag(Float.POSITIVE_INFINITY)
        assertEquals(0f, bar.hidden)
        bar.drag(Float.NEGATIVE_INFINITY)
        assertEquals(100f, bar.hidden)
    }

    @Test fun nanDragLeavesTheBarUsable() {
        val bar = BarCollapse(100f)
        bar.drag(-40f)
        bar.drag(Float.NaN)
        assertEquals(40f, bar.hidden)
        bar.drag(-10f)
        assertEquals(50f, bar.hidden)
    }

    @Test fun negativeMaxDoesNotThrow() {
        val bar = BarCollapse(100f)
        bar.setMax(-5f)
        assertEquals(0f, bar.hidden)
    }

    @Test fun collapsedStripTallerThanBarGivesZeroMaxHiddenAndNoClipping() {
        assertEquals(0f, BarGeometry.maxHidden(expanded = 120f, collapsed = 200f))
        assertEquals(0f, BarGeometry.clipping(hidden = 0f))
    }
}
