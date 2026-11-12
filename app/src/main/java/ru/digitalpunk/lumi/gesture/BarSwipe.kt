package ru.digitalpunk.lumi.gesture

import kotlin.math.abs

/** Decisions for the sideways swipe on the address field. Pure, so it runs on the JVM. */
object BarSwipe {
    enum class Outcome { Next, Previous, SpringBack }

    private const val COMMIT_FRACTION = 0.3f
    private const val RUBBER_BAND = 0.3f

    fun isHorizontal(dx: Float, dy: Float) = abs(dx) > abs(dy)

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
