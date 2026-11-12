package ru.digitalpunk.lumi.gesture

import ru.digitalpunk.lumi.engine.PageTouchInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PageGestureTrackerTest {
    private val events = mutableListOf<String>()
    private var expanded = true
    private val sink = object : PageGestureTracker.Sink {
        override fun barDrag(dy: Float) { events += "drag $dy" }
        override fun barRelease() { events += "release" }
        override fun pull(distancePx: Float) { events += "pull $distancePx" }
        override fun pullRelease(refresh: Boolean) { events += "pullRelease $refresh" }
    }
    private val tracker = PageGestureTracker(slopPx = 8f, pullThresholdPx = 80f, sink = sink, isBarExpanded = { expanded })

    private fun info(
        handles: Boolean = true,
        up: Boolean = false,
        down: Boolean = false,
        overscroll: Boolean = false,
    ) = PageTouchInfo(handles, up, down, overscroll)

    private val atTop = info(down = true, overscroll = true)
    private val midPage = info(up = true, down = true, overscroll = true)

    private fun start(info: PageTouchInfo) {
        tracker.down()
        tracker.resolved(info)
    }

    @Test fun fingerDownAtTopWithExpandedBarPulls() {
        start(atTop)
        tracker.move(20f)
        assertEquals(listOf("pull 6.0"), events)
    }

    @Test fun fingerDownAtTopWithCollapsedBarRevealsAndNeverPulls() {
        expanded = false
        start(atTop)
        tracker.move(20f)
        tracker.move(100f)
        assertEquals(listOf("drag 20.0", "drag 100.0"), events)
    }

    @Test fun fingerDownMidPageRevealsAndNeverPulls() {
        start(midPage)
        tracker.move(30f)
        assertEquals(listOf("drag 30.0"), events)
    }

    @Test fun fingerUpOnScrollablePageCollapses() {
        start(atTop)
        tracker.move(-20f)
        tracker.end(cancelled = false)
        assertEquals(listOf("drag -20.0", "release"), events)
    }

    @Test fun fingerUpWithNothingToScrollDoesNothing() {
        start(info(overscroll = true))
        tracker.move(-40f)
        tracker.end(cancelled = false)
        assertEquals(emptyList(), events)
    }

    @Test fun pageNotHandledByBrowserDoesNothing() {
        start(info(handles = false, up = true, down = true, overscroll = true))
        tracker.move(40f)
        tracker.move(-80f)
        tracker.end(cancelled = false)
        assertEquals(emptyList(), events)
    }

    @Test fun topWithoutOverscrollDoesNothingOnPullDown() {
        start(info(down = true))
        tracker.move(40f)
        tracker.end(cancelled = false)
        assertEquals(emptyList(), events)
    }

    @Test fun movementBeforeTheAsyncResultIsAppliedOnceItArrives() {
        tracker.down()
        tracker.move(-20f)
        assertEquals(emptyList(), events)
        tracker.resolved(midPage)
        assertEquals(listOf("drag -20.0"), events)
    }

    @Test fun resultAfterTheGestureEndedIsIgnored() {
        tracker.down()
        tracker.move(-20f)
        tracker.end(cancelled = false)
        tracker.resolved(midPage)
        assertEquals(emptyList(), events)
    }

    @Test fun travelBelowSlopDoesNothing() {
        start(atTop)
        tracker.move(5f)
        tracker.end(cancelled = false)
        assertEquals(emptyList(), events)
    }

    @Test fun pullDistanceIsHalfTheTravelAfterSlop() {
        start(atTop)
        tracker.move(8f + 40f)
        assertEquals(listOf("pull 20.0"), events)
    }

    @Test fun releasePastThresholdRefreshes() {
        start(atTop)
        tracker.move(8f + 160f)
        tracker.end(cancelled = false)
        assertEquals("pullRelease true", events.last())
    }

    @Test fun releaseShortOfThresholdDoesNotRefresh() {
        start(atTop)
        tracker.move(8f + 100f)
        tracker.end(cancelled = false)
        assertEquals("pullRelease false", events.last())
    }

    @Test fun cancelledPullNeitherRefreshesNorMovesTheBar() {
        start(atTop)
        tracker.move(8f + 200f)
        tracker.end(cancelled = true)
        assertEquals("pullRelease false", events.last())
        assertTrue(events.none { it.startsWith("drag") || it == "release" })
    }

    @Test fun pullBackToStartThenUpCollapses() {
        start(atTop)
        tracker.move(28f)
        tracker.move(-28f)
        tracker.move(-10f)
        tracker.end(cancelled = false)
        assertEquals(listOf("pull 10.0", "pullRelease false", "drag -10.0", "release"), events)
    }

    @Test fun barDragReversesWithinOneGesture() {
        start(midPage)
        tracker.move(-30f)
        tracker.move(50f)
        assertEquals(listOf("drag -30.0", "drag 50.0"), events)
    }
}
