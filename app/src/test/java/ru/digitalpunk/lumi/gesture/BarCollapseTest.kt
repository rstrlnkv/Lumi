package ru.digitalpunk.lumi.gesture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarCollapseTest {
    private val bar = BarCollapse(maxHidden = 100f)

    @Test fun startsExpanded() = assertTrue(bar.isExpanded)

    @Test fun fingerUpHidesBySameDistance() {
        bar.drag(-30f)
        assertEquals(30f, bar.hidden)
    }

    @Test fun fingerDownReveals() {
        bar.drag(-60f)
        bar.drag(20f)
        assertEquals(40f, bar.hidden)
    }

    @Test fun dragIsClamped() {
        bar.drag(-500f)
        assertEquals(100f, bar.hidden)
        assertTrue(bar.isCollapsed)
        bar.drag(500f)
        assertEquals(0f, bar.hidden)
    }

    @Test fun settleCollapsesAfterUpwardDragPastTwentyPercent() {
        bar.drag(-25f)
        assertEquals(100f, bar.settleTarget())
    }

    @Test fun settleStaysExpandedAfterTinyUpwardDrag() {
        bar.drag(-10f)
        assertEquals(0f, bar.settleTarget())
    }

    @Test fun settleExpandsAfterShortDownwardDragFromCollapsed() {
        bar.snapTo(bar.maxHidden)
        bar.drag(30f)
        assertEquals(0f, bar.settleTarget())
    }

    @Test fun settleStaysCollapsedAfterTinyDownwardDrag() {
        bar.snapTo(bar.maxHidden)
        bar.drag(10f)
        assertEquals(100f, bar.settleTarget())
    }

    @Test fun setMaxClampsHidden() {
        bar.snapTo(bar.maxHidden)
        bar.setMax(40f)
        assertEquals(40f, bar.hidden)
    }

    @Test fun expandShowsTheBar() {
        bar.snapTo(bar.maxHidden)
        bar.snapTo(0f)
        assertEquals(0f, bar.hidden)
        assertFalse(bar.isCollapsed)
    }
}
