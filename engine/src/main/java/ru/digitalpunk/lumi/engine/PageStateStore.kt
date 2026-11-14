package ru.digitalpunk.lumi.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Folds engine callbacks into one [PageState]. No GeckoView types, so it runs on the JVM. */
internal class PageStateStore(initial: PageState = PageState()) {
    private val mutable = MutableStateFlow(initial)
    val state: StateFlow<PageState> = mutable.asStateFlow()

    // While crashed only recovering() and locationChanged() change the state: late events from the dead page are ignored.
    fun pageStarted(url: String) = mutable.update {
        if (it.isCrashed) it else it.copy(url = url, isLoading = true, progress = 0, blockedTrackers = 0, hasBottomBar = false)
    }

    fun progressChanged(percent: Int) =
        mutable.update { if (it.isCrashed) it else it.copy(progress = percent.coerceIn(0, 100)) }

    fun pageStopped() = mutable.update { if (it.isCrashed) it else it.copy(isLoading = false) }

    // A same-document or history change to another site gets no pageStarted(), so the count resets here too.
    fun locationChanged(url: String?) = mutable.update {
        when {
            url == null -> it
            siteKey(url) != siteKey(it.url) -> it.copy(url = url, blockedTrackers = 0, hasBottomBar = false)
            else -> it.copy(url = url)
        }
    }

    fun canGoBackChanged(value: Boolean) = mutable.update { if (it.isCrashed) it else it.copy(canGoBack = value) }

    fun canGoForwardChanged(value: Boolean) = mutable.update { if (it.isCrashed) it else it.copy(canGoForward = value) }

    fun trackerBlocked(count: Int) = mutable.update { if (it.isCrashed) it else it.copy(blockedTrackers = count) }

    fun bottomBarChanged(value: Boolean) = mutable.update { if (it.isCrashed) it else it.copy(hasBottomBar = value) }

    fun historyChanged(items: List<HistoryEntry>, currentIndex: Int) =
        mutable.update { if (it.isCrashed) it else it.copy(history = backHistory(items, currentIndex)) }

    fun crashed() = mutable.update {
        it.copy(isCrashed = true, isLoading = false, progress = 0, canGoBack = false, canGoForward = false, history = BackHistory(), hasBottomBar = false)
    }

    fun recovering() = mutable.update { it.copy(isCrashed = false) }
}
