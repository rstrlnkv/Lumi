package ru.digitalpunk.lumi.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Folds engine callbacks into one [PageState]. No GeckoView types, so it runs on the JVM. */
internal class PageStateStore {
    private val mutable = MutableStateFlow(PageState())
    val state: StateFlow<PageState> = mutable.asStateFlow()

    // While crashed only recovering() and locationChanged() change the state: late events from the dead page are ignored.
    fun pageStarted(url: String) = mutable.update {
        if (it.isCrashed) it else it.copy(url = url, isLoading = true, progress = 0)
    }

    fun progressChanged(percent: Int) =
        mutable.update { if (it.isCrashed) it else it.copy(progress = percent.coerceIn(0, 100)) }

    fun pageStopped() = mutable.update { if (it.isCrashed) it else it.copy(isLoading = false) }

    fun locationChanged(url: String?) = mutable.update { if (url == null) it else it.copy(url = url) }

    fun canGoBackChanged(value: Boolean) = mutable.update { if (it.isCrashed) it else it.copy(canGoBack = value) }

    fun crashed() = mutable.update {
        it.copy(isCrashed = true, isLoading = false, progress = 0, canGoBack = false)
    }

    fun recovering() = mutable.update { it.copy(isCrashed = false) }
}
