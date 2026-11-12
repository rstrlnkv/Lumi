package ru.digitalpunk.lumi.gesture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BarSwipeTest {
    private val width = 300f
    private val fling = 1000f

    private fun outcome(dx: Float, velocity: Float = 0f, hasPrev: Boolean = true, hasNext: Boolean = true) =
        BarSwipe.outcome(dx, velocity, width, hasPrev, hasNext, flingPxPerSec = fling)

    @Test fun horizontalLocksWhenDxIsLarger() = assertTrue(BarSwipe.isHorizontal(dx = 12f, dy = 5f))

    @Test fun verticalMovementLocksNothing() {
        assertFalse(BarSwipe.isHorizontal(dx = 3f, dy = -12f))
        assertFalse(BarSwipe.isHorizontal(dx = 3f, dy = 12f))
    }

    @Test fun dragLeftPastThirtyPercentGoesToNextTab() = assertEquals(BarSwipe.Outcome.Next, outcome(dx = -90f))

    @Test fun dragRightPastThirtyPercentGoesToPreviousTab() = assertEquals(BarSwipe.Outcome.Previous, outcome(dx = 90f))

    @Test fun fastFlingBelowThirtyPercentStillSwitches() = assertEquals(BarSwipe.Outcome.Next, outcome(dx = -40f, velocity = -1500f))

    @Test fun slowShortDragSpringsBack() = assertEquals(BarSwipe.Outcome.SpringBack, outcome(dx = -40f, velocity = -200f))

    @Test fun flingAgainstTheDragDoesNotSwitch() = assertEquals(BarSwipe.Outcome.SpringBack, outcome(dx = -40f, velocity = 1500f))

    @Test fun atTheLastTabNothingSwitchesLeft() = assertEquals(BarSwipe.Outcome.SpringBack, outcome(dx = -200f, velocity = -3000f, hasNext = false))

    @Test fun atTheFirstTabNothingSwitchesRight() = assertEquals(BarSwipe.Outcome.SpringBack, outcome(dx = 200f, velocity = 3000f, hasPrev = false))

    @Test fun rubberBandMovesAThirdAsFarAtTheEnd() = assertEquals(-30f, BarSwipe.follow(dx = -100f, hasPrev = true, hasNext = false), 0.001f)

    @Test fun dragFollowsTheFingerWhenThereIsATarget() = assertEquals(-100f, BarSwipe.follow(dx = -100f, hasPrev = false, hasNext = true))
}

class BarSwipeVerticalTest {
    private val threshold = 48f
    private val fling = 1000f

    @Test fun moreSidewaysThanUpLocksHorizontal() = assertEquals(BarSwipe.Axis.Horizontal, BarSwipe.lockAxis(dx = 12f, dy = -5f))

    @Test fun moreUpThanSidewaysLocksUp() = assertEquals(BarSwipe.Axis.Up, BarSwipe.lockAxis(dx = 3f, dy = -12f))

    @Test fun upwardOneAndAHalfTimesTheSidewaysMoveLocksUp() = assertEquals(BarSwipe.Axis.Up, BarSwipe.lockAxis(dx = 8f, dy = -12f))

    @Test fun upwardJustUnderOneAndAHalfTimesTheSidewaysMoveDoesNotLockUp() = assertEquals(BarSwipe.Axis.None, BarSwipe.lockAxis(dx = 8f, dy = -11.9f))

    @Test fun downwardLocksNothing() = assertEquals(BarSwipe.Axis.None, BarSwipe.lockAxis(dx = 3f, dy = 12f))

    @Test fun upwardDragOfFortyEightDpOpensTheGrid() = assertTrue(BarSwipe.opensGrid(dy = -48f, velocityY = 0f, thresholdPx = threshold, flingPxPerSec = fling))

    @Test fun shortSlowUpwardDragDoesNot() = assertFalse(BarSwipe.opensGrid(dy = -20f, velocityY = -300f, thresholdPx = threshold, flingPxPerSec = fling))

    @Test fun shortFastUpwardFlingOpensTheGrid() = assertTrue(BarSwipe.opensGrid(dy = -20f, velocityY = -1500f, thresholdPx = threshold, flingPxPerSec = fling))

    @Test fun fastDownwardFlingNeverOpensTheGrid() = assertFalse(BarSwipe.opensGrid(dy = 20f, velocityY = 1500f, thresholdPx = threshold, flingPxPerSec = fling))

    @Test fun upwardDragThatEndsBackDownDoesNotOpen() = assertFalse(BarSwipe.opensGrid(dy = -10f, velocityY = 1500f, thresholdPx = threshold, flingPxPerSec = fling))
}

class BarSwipeVerticalEdgeTest {
    private val threshold = 48f
    private val fling = 1000f

    @Test fun exactDiagonalTieUpLocksNothing() = assertEquals(BarSwipe.Axis.None, BarSwipe.lockAxis(dx = 5f, dy = -5f))

    @Test fun exactDiagonalTieDownLocksNothing() = assertEquals(BarSwipe.Axis.None, BarSwipe.lockAxis(dx = 5f, dy = 5f))

    @Test fun noMovementLocksNothing() = assertEquals(BarSwipe.Axis.None, BarSwipe.lockAxis(0f, 0f))

    @Test fun flingOfExactlyOneThousandOpensTheGrid() = assertTrue(BarSwipe.opensGrid(dy = -10f, velocityY = -1000f, thresholdPx = threshold, flingPxPerSec = fling))

    @Test fun flingJustUnderOneThousandDoesNot() = assertFalse(BarSwipe.opensGrid(dy = -10f, velocityY = -999f, thresholdPx = threshold, flingPxPerSec = fling))

    @Test fun dragJustUnderFortyEightDoesNot() = assertFalse(BarSwipe.opensGrid(dy = -47.9f, velocityY = 0f, thresholdPx = threshold, flingPxPerSec = fling))

    @Test fun downwardDragEndingWithAnUpwardFlingDoesNotOpen() = assertFalse(BarSwipe.opensGrid(dy = 10f, velocityY = -1500f, thresholdPx = threshold, flingPxPerSec = fling))

    @Test fun noNetMovementDoesNotOpenEvenWithHugeVelocity() = assertFalse(BarSwipe.opensGrid(dy = 0f, velocityY = -9000f, thresholdPx = threshold, flingPxPerSec = fling))

    @Test fun veryLongDownwardDragNeverOpens() = assertFalse(BarSwipe.opensGrid(dy = 5000f, velocityY = 0f, thresholdPx = threshold, flingPxPerSec = fling))
}
