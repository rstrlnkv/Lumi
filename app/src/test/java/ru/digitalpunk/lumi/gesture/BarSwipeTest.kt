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
