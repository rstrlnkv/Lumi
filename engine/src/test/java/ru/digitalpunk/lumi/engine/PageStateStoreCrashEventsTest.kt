package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Late events after a content-process crash. */
class PageStateStoreCrashEventsTest {
    private val store = PageStateStore()
    private val state get() = store.state.value

    private fun crashedOnLoadingPage() {
        store.pageStarted("https://a.example/")
        store.canGoBackChanged(true)
        store.crashed()
    }

    @Test fun locationChangeWhileCrashedKeepsCrashedAndUpdatesUrl() {
        crashedOnLoadingPage()
        store.locationChanged("https://b.example/")
        assertTrue(state.isCrashed)
        assertEquals("https://b.example/", state.url)
    }

    @Test fun lateProgressAfterCrashKeepsProgressZero() {
        crashedOnLoadingPage()
        store.progressChanged(80)
        assertEquals(0, state.progress)
    }

    @Test fun lateCanGoBackAfterCrashStaysFalse() {
        crashedOnLoadingPage()
        store.canGoBackChanged(true)
        assertFalse(state.canGoBack)
    }

    @Test fun latePageStartAfterCrashDoesNotHideCrashPanel() {
        crashedOnLoadingPage()
        store.pageStarted("https://a.example/")
        assertTrue(state.isCrashed)
    }

    @Test fun latePageStopAfterCrashKeepsNotLoading() {
        crashedOnLoadingPage()
        store.pageStopped()
        assertFalse(state.isLoading)
    }

    @Test fun recoveringKeepsUrlAndBackStaysFalse() {
        crashedOnLoadingPage()
        store.recovering()
        assertFalse(state.isCrashed)
        assertEquals("https://a.example/", state.url)
        assertFalse(state.canGoBack)
    }

    @Test fun pageStopAfterCrashDoesNotUncrash() {
        crashedOnLoadingPage()
        store.pageStopped()
        assertTrue(state.isCrashed)
    }
}
