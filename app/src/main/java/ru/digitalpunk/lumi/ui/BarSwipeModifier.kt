package ru.digitalpunk.lumi.ui

import android.os.Build
import androidx.compose.animation.core.animate
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.digitalpunk.lumi.engine.TabId
import ru.digitalpunk.lumi.gesture.BarSwipe

/**
 * How far the content of the address field is shifted by a swipe, in px (negative = towards the next tab), and the
 * width of that content. [settling] is true while the old text is out and the new tab has not arrived yet.
 */
class BarSwipeState {
    var offset by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(0f)
    var settling by mutableStateOf(false)
    private var job: Job? = null

    private suspend fun animateTo(target: Float) = animate(offset, target) { value, _ -> offset = value }

    /** The old text slides out, the tab switches, and once it has arrived the new text slides in from the other side. */
    fun settle(scope: CoroutineScope, outcome: BarSwipe.Outcome, tabs: TabActions, selectedId: () -> TabId?) {
        job?.cancel()
        job = scope.launch {
            val direction = when (outcome) {
                BarSwipe.Outcome.Next -> -1
                BarSwipe.Outcome.Previous -> 1
                BarSwipe.Outcome.SpringBack -> 0
            }
            if (direction != 0) {
                try {
                    animateTo(direction * width)
                    settling = true
                    val before = selectedId()
                    tabs.switchBy(-direction)
                    withTimeoutOrNull(ARRIVAL_TIMEOUT_MILLIS) { snapshotFlow(selectedId).first { it != before } }
                    offset = -direction * width
                    animateTo(0f)
                } finally {
                    settling = false
                }
            } else {
                animateTo(0f)
            }
        }
    }

    /** Drops any running slide and puts the content back; for when the gesture is switched off. */
    fun reset() {
        job?.cancel()
        offset = 0f
        settling = false
    }
}

/**
 * Swipe sideways to switch tabs, or up to open the tab grid. It locks only when the first move is sideways or up,
 * so taps and downward touches reach the field untouched. [state] carries the shift for the field to draw.
 */
@Composable
fun Modifier.barSwipe(state: BarSwipeState, tabs: TabActions, scope: CoroutineScope): Modifier {
    val haptics = LocalHapticFeedback.current
    val currentTabs by rememberUpdatedState(tabs)
    val settle: (BarSwipe.Outcome) -> Unit = { state.settle(scope, it, currentTabs) { currentTabs.selectedId } }
    return this
        .pointerInput(state) {
            val flingPxPerSec = FLING_DP_PER_SEC.dp.toPx()
            val gridPx = GRID_DP.dp.toPx()
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val velocity = VelocityTracker().also { it.addPointerInputChange(down) }
                var total = Offset.Zero
                var axis = BarSwipe.Axis.None
                var locked = false
                var pastThreshold = false
                var finished = false
                try {
                    while (true) {
                        val change: PointerInputChange = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                        if (change.isConsumed) return@awaitEachGesture
                        total += change.positionChange()
                        velocity.addPointerInputChange(change)
                        if (!locked) {
                            if (!change.pressed) return@awaitEachGesture
                            if (total.getDistance() < viewConfiguration.touchSlop) continue
                            axis = BarSwipe.lockAxis(total.x, total.y)
                            if (axis == BarSwipe.Axis.None) return@awaitEachGesture
                            locked = true
                        }
                        if (axis == BarSwipe.Axis.Up) {
                            change.consume()
                            if (!change.pressed) {
                                finished = true
                                if (BarSwipe.opensGrid(total.y, velocity.calculateVelocity().y, gridPx, flingPxPerSec)) {
                                    haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                                    currentTabs.openGrid()
                                }
                                return@awaitEachGesture
                            }
                        } else if (change.pressed) {
                            change.consume()
                            val hasPrev = currentTabs.prevHost != null
                            val hasNext = currentTabs.nextHost != null
                            state.offset = BarSwipe.follow(total.x, hasPrev, hasNext)
                            val canSwitch = if (total.x < 0) hasNext else hasPrev
                            val beyond = canSwitch && BarSwipe.outcome(total.x, 0f, state.width, hasPrev, hasNext, flingPxPerSec) != BarSwipe.Outcome.SpringBack
                            if (beyond && !pastThreshold) {
                                haptics.performHapticFeedback(
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) HapticFeedbackType.SegmentTick else HapticFeedbackType.TextHandleMove,
                                )
                            }
                            pastThreshold = beyond
                        } else {
                            finished = true
                            val outcome = BarSwipe.outcome(
                                total.x, velocity.calculateVelocity().x, state.width,
                                currentTabs.prevHost != null, currentTabs.nextHost != null, flingPxPerSec,
                            )
                            settle(outcome)
                            return@awaitEachGesture
                        }
                    }
                } finally {
                    // The gesture was cut off (for example the field started editing): do not leave the text shifted.
                    if (locked && !finished && axis == BarSwipe.Axis.Horizontal) settle(BarSwipe.Outcome.SpringBack)
                }
            }
        }
}

private const val FLING_DP_PER_SEC = 1000
private const val GRID_DP = 48
private const val ARRIVAL_TIMEOUT_MILLIS = 500L
