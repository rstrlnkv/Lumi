package ru.digitalpunk.lumi.gesture

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/** The refresh indicator stays at least this long, so a fast reload still shows it. */
const val MIN_REFRESH_MILLIS = 600L

/** A refresh is over once the page stops loading, or crashes. */
fun pageSettled(isLoading: Boolean, isCrashed: Boolean): Boolean = !isLoading || isCrashed

/** Waits [minMillis], then until [settled] reports true. */
suspend fun awaitRefreshEnd(minMillis: Long, settled: Flow<Boolean>) {
    delay(minMillis)
    settled.first { it }
}
