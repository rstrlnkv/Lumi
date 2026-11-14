package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageStateStoreBottomBarTest {
    private val store = PageStateStore()
    private val state get() = store.state.value

    @Test fun aNewPageHasNoBottomBar() = assertFalse(state.hasBottomBar)

    @Test fun aReportSetsAndClearsTheFlag() {
        store.bottomBarChanged(true)
        assertTrue(state.hasBottomBar)
        store.bottomBarChanged(false)
        assertFalse(state.hasBottomBar)
    }

    @Test fun aNewLoadClearsTheFlagUntilTheScriptReportsAgain() {
        store.bottomBarChanged(true)
        store.pageStarted("https://b.com/")
        assertFalse(state.hasBottomBar)
    }

    @Test fun aLocationChangeToAnotherSiteClearsTheFlag() {
        store.pageStarted("https://a.com/")
        store.bottomBarChanged(true)
        store.locationChanged("https://b.com/")
        assertFalse(state.hasBottomBar)
    }

    @Test fun aLocationChangeWithinTheSiteKeepsTheFlag() {
        store.pageStarted("https://a.com/")
        store.bottomBarChanged(true)
        store.locationChanged("https://a.com/cart#top")
        assertTrue(state.hasBottomBar)
    }

    @Test fun aCrashClearsTheFlagAndALateReportIsIgnored() {
        store.bottomBarChanged(true)
        store.crashed()
        assertFalse(state.hasBottomBar)
        store.bottomBarChanged(true)
        assertFalse(state.hasBottomBar)
    }
}
