package ru.digitalpunk.lumi.gesture

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

class RefreshRulesTest {
    @Test fun aPageStillLoadingAndNotCrashedIsNotSettled() = assertFalse(pageSettled(isLoading = true, isCrashed = false))

    @Test fun aPageThatStoppedLoadingIsSettled() = assertTrue(pageSettled(isLoading = false, isCrashed = false))

    @Test fun aPageThatCrashedWhileLoadingIsSettled() = assertTrue(pageSettled(isLoading = true, isCrashed = true))

    @Test fun awaitRefreshEndWaitsTheMinimumEvenWhenTheFirstValueIsSettled() = runBlocking<Unit> {
        val start = System.nanoTime()
        withTimeout(2_000) { awaitRefreshEnd(200, flowOf(true)) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(elapsedMs >= 190, "returned after $elapsedMs ms")
    }

    @Test fun awaitRefreshEndReturnsOnceTheFlowSettlesAfterTheMinimum() = runBlocking<Unit> {
        val settled = MutableStateFlow(false)
        launch {
            delay(150)
            settled.value = true
        }
        assertEquals(Unit, withTimeoutOrNull(2_000) { awaitRefreshEnd(50, settled) })
    }

    @Test fun awaitRefreshEndKeepsWaitingWhileTheFlowNeverSettles() = runBlocking<Unit> {
        assertNull(withTimeoutOrNull(300) { awaitRefreshEnd(10, MutableStateFlow(false)) })
    }

    @Test fun aCachedReloadThatEndedEarlyEndsRightAfterTheMinimum() = runBlocking<Unit> {
        val start = System.nanoTime()
        withTimeout(2_000) { awaitRefreshEnd(50, MutableStateFlow(true)) }
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertTrue(elapsedMs in 45..1_000, "returned after $elapsedMs ms")
    }

    @Test fun aReloadThatErrorsAfterTheMinimumStillEnds() = runBlocking<Unit> {
        // The page stops loading with an error: isLoading false, not crashed.
        val settled = MutableStateFlow(pageSettled(isLoading = true, isCrashed = false))
        launch {
            delay(100)
            settled.value = pageSettled(isLoading = false, isCrashed = false)
        }
        assertEquals(Unit, withTimeoutOrNull(2_000) { awaitRefreshEnd(20, settled) })
    }

    @Test fun aSettleThatArrivesBeforeTheMinimumIsNotMissed() = runBlocking<Unit> {
        // The load ends at 20 ms and a new one starts at 60 ms; the wait began at 0 and the minimum is 200.
        // The flow is read only after the minimum, so it reports the current state: unsettled here, and it must keep waiting.
        val settled = MutableStateFlow(false)
        launch {
            delay(20)
            settled.value = true
            delay(40)
            settled.value = false
        }
        assertNull(withTimeoutOrNull(500) { awaitRefreshEnd(200, settled) })
    }

    @Test fun cancellingTheWaitDuringTheMinimumNeverReadsTheFlow() = runBlocking<Unit> {
        var collected = false
        val job = launch { awaitRefreshEnd(10_000, kotlinx.coroutines.flow.flow { collected = true; emit(true) }) }
        delay(50)
        job.cancel()
        job.join()
        assertFalse(collected)
    }

    @Test fun aZeroMinimumEndsAtOnceOnASettledFlow() = runBlocking<Unit> {
        assertEquals(Unit, withTimeoutOrNull(1_000) { awaitRefreshEnd(0, flowOf(true)) })
    }

    @Test fun anEmptyFlowThrowsInsteadOfHangingTheIndicator() = runBlocking<Unit> {
        // snapshotFlow never completes, but a completed flow must not look like a settled page.
        val result = runCatching { withTimeout(1_000) { awaitRefreshEnd(0, kotlinx.coroutines.flow.emptyFlow()) } }
        assertTrue(result.isFailure)
    }
}
