package ru.digitalpunk.lumi.gesture

import kotlin.math.abs

/** Decisions for the swipes on the address field: sideways switches tabs, up opens the grid. Pure, so it runs on the JVM. */
object BarSwipe {
    enum class Outcome { Next, Previous, SpringBack }

    enum class Axis { Horizontal, Up, None }

    private const val COMMIT_FRACTION = 0.3f
    private const val RUBBER_BAND = 0.3f
    private const val UP_RATIO = 1.5f

    fun isHorizontal(dx: Float, dy: Float) = abs(dx) > abs(dy)

    /** Which swipe the first move starts: up (only when clearly upward, 1.5 times as far up as sideways), sideways, or none (a downward or diagonal move is left to the field). */
    fun lockAxis(dx: Float, dy: Float): Axis = when {
        dy < 0 && -dy >= UP_RATIO * abs(dx) -> Axis.Up
        isHorizontal(dx, dy) -> Axis.Horizontal
        else -> Axis.None
    }

    /** An upward drag of [thresholdPx], or an upward fling of [flingPxPerSec] that is still going up on release. */
    fun opensGrid(dy: Float, velocityY: Float, thresholdPx: Float, flingPxPerSec: Float): Boolean =
        dy < 0 && (-dy >= thresholdPx || -velocityY >= flingPxPerSec)

    /** How far the field content moves for a finger drag of [dx]: one to one, or 0.3 times as far where no tab lies beyond. */
    fun follow(dx: Float, hasPrev: Boolean, hasNext: Boolean): Float {
        val hasTarget = if (dx < 0) hasNext else hasPrev
        return if (hasTarget) dx else dx * RUBBER_BAND
    }

    /** Left goes to the next tab and right to the previous one, as in Safari. */
    fun outcome(dx: Float, velocityX: Float, width: Float, hasPrev: Boolean, hasNext: Boolean, flingPxPerSec: Float): Outcome {
        val farEnough = abs(dx) >= width * COMMIT_FRACTION
        val fastEnough = abs(velocityX) >= flingPxPerSec && velocityX * dx > 0f
        if (!farEnough && !fastEnough) return Outcome.SpringBack
        return when {
            dx < 0 && hasNext -> Outcome.Next
            dx > 0 && hasPrev -> Outcome.Previous
            else -> Outcome.SpringBack
        }
    }
}
