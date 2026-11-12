package ru.digitalpunk.lumi.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.gesture.BarCollapse

/** [BarCollapse] as Compose state, with the settle animation. Read [hidden] in a layer lambda so scrolling does not recompose. */
@Stable
class BarController(private val scope: CoroutineScope) {
    private val collapse = BarCollapse(maxHidden = 0f)
    private var animation: Job? = null

    var hidden by mutableFloatStateOf(0f)
        private set
    var maxHidden by mutableFloatStateOf(0f)
        private set

    val isExpanded get() = collapse.isExpanded
    val isCollapsed get() = maxHidden > 0f && collapse.isCollapsed

    fun setMax(value: Float) {
        collapse.setMax(value)
        publish()
    }

    fun drag(dy: Float) {
        animation?.cancel()
        collapse.drag(dy)
        publish()
    }

    /** After a drag: slide to the nearer end. */
    fun settle() = slideTo(collapse.settleTarget())

    fun expand() {
        if (!collapse.isExpanded) slideTo(0f)
    }

    private fun slideTo(target: Float) {
        animation?.cancel()
        animation = scope.launch {
            animate(collapse.hidden, target, animationSpec = tween(SETTLE_MILLIS)) { value, _ ->
                collapse.snapTo(value)
                publish()
            }
        }
    }

    private fun publish() {
        hidden = collapse.hidden
        maxHidden = collapse.maxHidden
    }

    private companion object {
        const val SETTLE_MILLIS = 150
    }
}
