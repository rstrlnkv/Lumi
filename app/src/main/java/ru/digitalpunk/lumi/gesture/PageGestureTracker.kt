package ru.digitalpunk.lumi.gesture

import ru.digitalpunk.lumi.engine.PageTouchInfo
import kotlin.math.abs

/** Distance the pull indicator shows, 0..1 at the refresh threshold (it may go above 1). */
fun pullFraction(renderedPullPx: Float, thresholdPx: Float) = if (thresholdPx <= 0f) 0f else renderedPullPx / thresholdPx

/**
 * Decides what one finger gesture over the page does: slide the address bar, pull to refresh, or
 * nothing. It never consumes the touch; the page scrolls as usual. Rules (first match wins):
 * R0 browser does not handle the touch: ignore. Finger down: R1 bar expanded and page at top and
 * can overscroll: pull; R2 bar not fully expanded: reveal; R3 page can scroll up: reveal; R4 else
 * ignore. Finger up: R5 page can scroll down: collapse; R6 else ignore.
 */
class PageGestureTracker(
    private val slopPx: Float,
    private val pullThresholdPx: Float,
    private val sink: Sink,
    private val isBarExpanded: () -> Boolean,
) {
    interface Sink {
        fun barDrag(dy: Float)
        fun barRelease()
        fun pull(distancePx: Float)
        fun pullRelease(refresh: Boolean)
    }

    private enum class Mode { Idle, Undecided, Ignored, BarDrag, Pull }

    private var mode = Mode.Idle
    private var info: PageTouchInfo? = null
    private var total = 0f
    private var rendered = 0f

    fun down() {
        end(cancelled = true)
        mode = Mode.Undecided
        info = null
        total = 0f
        rendered = 0f
    }

    fun resolved(info: PageTouchInfo) {
        if (mode == Mode.Idle) return
        this.info = info
        if (mode == Mode.Undecided) decide()
    }

    fun move(dy: Float) {
        if (dy.isNaN()) return
        when (mode) {
            Mode.Undecided -> {
                total += dy
                decide()
            }
            Mode.BarDrag -> sink.barDrag(dy)
            Mode.Pull -> movePull(dy)
            Mode.Idle, Mode.Ignored -> Unit
        }
    }

    fun end(cancelled: Boolean) {
        when (mode) {
            Mode.BarDrag -> sink.barRelease()
            Mode.Pull -> sink.pullRelease(refresh = !cancelled && rendered >= pullThresholdPx)
            else -> Unit
        }
        mode = Mode.Idle
    }

    private fun decide() {
        val info = info ?: return
        if (abs(total) < slopPx) return
        val fingerDown = total > 0
        mode = when {
            !info.browserHandles -> Mode.Ignored
            fingerDown && isBarExpanded() && info.canPullToRefresh -> Mode.Pull
            fingerDown && (!isBarExpanded() || info.canScrollUp) -> Mode.BarDrag
            !fingerDown && info.canScrollDown -> Mode.BarDrag
            else -> Mode.Ignored
        }
        when (mode) {
            Mode.BarDrag -> sink.barDrag(total)
            Mode.Pull -> movePull(0f)
            else -> Unit
        }
    }

    private fun movePull(dy: Float) {
        total += dy
        val distance = (total - slopPx).coerceAtLeast(0f)
        if (distance == 0f && dy < 0 && info?.canScrollDown == true) {
            sink.pullRelease(refresh = false)
            mode = Mode.BarDrag
            return
        }
        rendered = distance * PULL_RESISTANCE
        sink.pull(rendered)
    }

    private companion object {
        const val PULL_RESISTANCE = 0.5f
    }
}
