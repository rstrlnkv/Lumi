package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageStateStoreTest {
    private val store = PageStateStore()
    private val state get() = store.state.value

    @Test fun initialStateIsBlankAndIdle() = assertEquals(PageState(), state)

    @Test fun pageStartSetsLoadingAndResetsProgress() {
        store.progressChanged(70)
        store.pageStarted("https://a.com")
        assertTrue(state.isLoading)
        assertEquals(0, state.progress)
    }

    @Test fun pageStartUpdatesUrl() {
        store.pageStarted("https://a.com")
        assertEquals("https://a.com", state.url)
    }

    @Test fun progressAbove100Clamps() {
        store.progressChanged(150)
        assertEquals(100, state.progress)
    }

    @Test fun negativeProgressClampsToZero() {
        store.progressChanged(-5)
        assertEquals(0, state.progress)
    }

    @Test fun pageStopClearsLoading() {
        store.pageStarted("https://a.com")
        store.pageStopped()
        assertFalse(state.isLoading)
    }

    @Test fun locationChangeUpdatesUrl() {
        store.locationChanged("https://b.com")
        assertEquals("https://b.com", state.url)
    }

    @Test fun nullLocationKeepsUrl() {
        store.locationChanged("https://b.com")
        store.locationChanged(null)
        assertEquals("https://b.com", state.url)
    }

    @Test fun canGoBackFollows() {
        store.canGoBackChanged(true)
        assertTrue(state.canGoBack)
        store.canGoBackChanged(false)
        assertFalse(state.canGoBack)
    }

    @Test fun crashClearsLoadingProgressAndBackAndSetsCrashed() {
        store.pageStarted("https://a.com")
        store.progressChanged(40)
        store.canGoBackChanged(true)
        store.crashed()
        assertTrue(state.isCrashed)
        assertFalse(state.isLoading)
        assertEquals(0, state.progress)
        assertFalse(state.canGoBack)
    }

    @Test fun crashWhileCrashedStaysCrashed() {
        store.crashed()
        store.crashed()
        assertTrue(state.isCrashed)
    }

    @Test fun pageStartAfterRecoveringClearsCrashed() {
        store.crashed()
        store.recovering()
        store.pageStarted("https://a.com")
        assertFalse(state.isCrashed)
    }
}
