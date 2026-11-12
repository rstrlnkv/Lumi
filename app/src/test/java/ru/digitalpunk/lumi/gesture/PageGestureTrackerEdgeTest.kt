package ru.digitalpunk.lumi.gesture

import ru.digitalpunk.lumi.engine.PageTouchInfo
import kotlin.test.Test
import kotlin.test.assertEquals

/** Inputs the main tracker tests do not feed: out-of-order, repeated and non-finite events. */
class PageGestureTrackerEdgeTest {
    private val events = mutableListOf<String>()
    private val sink = object : PageGestureTracker.Sink {
        override fun barDrag(dy: Float) { events += "drag $dy" }
        override fun barRelease() { events += "release" }
        override fun pull(distancePx: Float) { events += "pull $distancePx" }
        override fun pullRelease(refresh: Boolean) { events += "pullRelease $refresh" }
    }
    private val tracker = PageGestureTracker(slopPx = 8f, pullThresholdPx = 80f, sink = sink, isBarExpanded = { true })
    private val atTop = PageTouchInfo(browserHandles = true, canScrollUp = false, canScrollDown = true, canOverscrollVertically = true)
    private val midPage = PageTouchInfo(true, canScrollUp = true, canScrollDown = true, canOverscrollVertically = true)

    @Test fun moveAndEndBeforeAnyDownDoNothing() {
        tracker.move(50f)
        tracker.move(-50f)
        tracker.end(cancelled = false)
        tracker.end(cancelled = true)
        assertEquals(emptyList(), events)
    }

    @Test fun resolvedBeforeAnyDownDoesNotStartAGesture() {
        tracker.resolved(midPage)
        tracker.move(50f)
        assertEquals(emptyList(), events)
    }

    @Test fun staleResultAfterEndDoesNotLeakIntoTheNextGesture() {
        tracker.down()
        tracker.end(cancelled = false)
        tracker.resolved(midPage)
        tracker.down()
        tracker.move(-30f) // new gesture has no result yet: must wait, not reuse the stale one
        assertEquals(emptyList(), events)
    }

    @Test fun gestureWithoutResultThatEndsLeavesNothingBehind() {
        tracker.down()
        tracker.move(-30f)
        tracker.end(cancelled = false)
        tracker.down()
        tracker.resolved(midPage)
        tracker.move(-20f)
        assertEquals(listOf("drag -20.0"), events)
    }

    @Test fun secondDownWhileBarDragIsRunningReleasesTheBar() {
        tracker.down()
        tracker.resolved(midPage)
        tracker.move(-30f)
        tracker.down() // the end of the first gesture never arrived
        assertEquals(listOf("drag -30.0", "release"), events)
    }

    @Test fun secondDownWhilePullIsRunningClosesThePullIndicator() {
        tracker.down()
        tracker.resolved(atTop)
        tracker.move(40f)
        tracker.down()
        assertEquals(listOf("pull 16.0", "pullRelease false"), events)
    }

    @Test fun pullPastThresholdThenBackBelowItDoesNotRefresh() {
        tracker.down()
        tracker.resolved(atTop)
        tracker.move(8f + 200f) // rendered 100, past 80
        tracker.move(-150f) // rendered 25
        tracker.end(cancelled = false)
        assertEquals("pullRelease false", events.last())
    }

    @Test fun pullBackOverThresholdAgainRefreshes() {
        tracker.down()
        tracker.resolved(atTop)
        tracker.move(8f + 200f)
        tracker.move(-150f)
        tracker.move(150f)
        tracker.end(cancelled = false)
        assertEquals("pullRelease true", events.last())
    }

    @Test fun travelExactlyAtSlopDecidesTheGesture() {
        tracker.down()
        tracker.resolved(midPage)
        tracker.move(-8f)
        assertEquals(listOf("drag -8.0"), events)
    }

    @Test fun oppositeTravelBeforeTheResultCancelsOut() {
        tracker.down()
        tracker.move(-30f)
        tracker.move(30f)
        tracker.resolved(midPage)
        assertEquals(emptyList(), events)
    }

    @Test fun nanTravelNeverReachesTheBar() {
        tracker.down()
        tracker.resolved(midPage)
        tracker.move(Float.NaN)
        tracker.end(cancelled = false)
        assertEquals(emptyList(), events.filter { it.contains("NaN") })
    }

    @Test fun pullFractionHandlesZeroAndNegativeThreshold() {
        assertEquals(0f, pullFraction(50f, 0f))
        assertEquals(0f, pullFraction(50f, -10f))
        assertEquals(1.5f, pullFraction(120f, 80f))
    }
}
