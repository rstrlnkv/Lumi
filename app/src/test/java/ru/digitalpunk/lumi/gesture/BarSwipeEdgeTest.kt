package ru.digitalpunk.lumi.gesture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarSwipeEdgeTest {
    private fun outcome(dx: Float, v: Float = 0f, width: Float = 300f, prev: Boolean = true, next: Boolean = true) =
        BarSwipe.outcome(dx, v, width, prev, next, flingPxPerSec = 1000f)

    @Test fun justUnderThirtyPercentSpringsBack() = assertEquals(BarSwipe.Outcome.SpringBack, outcome(dx = -89f))

    @Test fun exactlyThirtyPercentSwitches() = assertEquals(BarSwipe.Outcome.Next, outcome(dx = -90f))

    @Test fun justUnderFlingSpeedSpringsBack() = assertEquals(BarSwipe.Outcome.SpringBack, outcome(dx = -10f, v = -999f))

    @Test fun exactlyFlingSpeedSwitches() = assertEquals(BarSwipe.Outcome.Next, outcome(dx = -10f, v = -1000f))

    @Test fun zeroWidthWithNoMovementSpringsBack() =
        assertEquals(BarSwipe.Outcome.SpringBack, outcome(dx = 0f, width = 0f))

    @Test fun zeroWidthDoesNotCrashOnAnyDrag() {
        assertEquals(BarSwipe.Outcome.Next, outcome(dx = -1f, width = 0f))
        assertEquals(BarSwipe.Outcome.SpringBack, outcome(dx = -1f, width = 0f, next = false))
    }

    @Test fun nanVelocityDoesNotSwitchOnAShortDrag() =
        assertEquals(BarSwipe.Outcome.SpringBack, outcome(dx = -10f, v = Float.NaN))

    @Test fun leftwardHorizontalMovementLocks() = assertTrue(BarSwipe.isHorizontal(dx = -12f, dy = 5f))

    @Test fun exactDiagonalLocksNothing() = assertFalse(BarSwipe.isHorizontal(dx = 5f, dy = 5f))

    @Test fun noMovementLocksNothing() = assertFalse(BarSwipe.isHorizontal(0f, 0f))

    @Test fun rubberBandAtBothEndsWhenAlone() {
        assertEquals(30f, BarSwipe.follow(100f, hasPrev = false, hasNext = false), 0.001f)
        assertEquals(-30f, BarSwipe.follow(-100f, hasPrev = false, hasNext = false), 0.001f)
    }

    @Test fun rightDragFollowsOneToOneWhenPreviousExists() =
        assertEquals(100f, BarSwipe.follow(100f, hasPrev = true, hasNext = false), 0.001f)

    @Test fun rightDragRubberBandsWhenOnlyNextExists() =
        assertEquals(30f, BarSwipe.follow(100f, hasPrev = false, hasNext = true), 0.001f)
}
