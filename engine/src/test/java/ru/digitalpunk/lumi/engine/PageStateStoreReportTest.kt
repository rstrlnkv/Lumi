package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The page report in one tab's state: applied at once, cleared until the page reports again, never carried to another site. */
class PageStateStoreReportTest {
    private val store = PageStateStore()
    private val state get() = store.state.value
    private val full = PageReport(true, 0xFF0B57D0.toInt(), 0xFF1A73E8.toInt())

    private fun assertCleared() {
        assertFalse(state.hasBottomBar)
        assertEquals(null, state.bottomBarColor)
        assertEquals(null, state.statusBarColor)
    }

    @Test fun aNewPageHasNoReport() = assertCleared()

    @Test fun aReportSetsAllThreeFieldsInOneUpdateAndNothingElse() {
        store.pageStarted("https://a.com/")
        store.progressChanged(40)
        val before = state
        store.reported(full)
        store.reported(full)
        assertEquals(before.copy(hasBottomBar = true, bottomBarColor = 0xFF0B57D0.toInt(), statusBarColor = 0xFF1A73E8.toInt()), state)
        store.reported(PageReport(false, null, 0xFF202124.toInt()))
        assertEquals(before.copy(statusBarColor = 0xFF202124.toInt()), state)
    }

    @Test fun aNewLoadClearsTheReportUntilTheScriptReportsAgain() {
        store.pageStarted("https://a.com/")
        store.reported(full)
        store.pageStarted("https://a.com/")
        assertCleared()
    }

    @Test fun aLocationChangeToAnotherSiteClearsTheReport() {
        store.pageStarted("https://a.com/")
        store.reported(full)
        store.locationChanged("https://b.com/")
        assertCleared()
    }

    @Test fun aLocationChangeWithinTheSiteAndOtherPageEventsKeepTheReport() {
        store.pageStarted("https://a.com/")
        store.reported(full)
        store.progressChanged(50)
        store.pageStopped()
        store.canGoBackChanged(true)
        store.trackerBlocked(3)
        store.locationChanged(null)
        store.locationChanged("https://WWW.A.com:8443/other?x=1#y")
        assertTrue(state.hasBottomBar)
        assertEquals(0xFF0B57D0.toInt(), state.bottomBarColor)
        assertEquals(0xFF1A73E8.toInt(), state.statusBarColor)
    }

    @Test fun aCrashClearsTheReportAndALateReportIsIgnored() {
        store.reported(full)
        store.crashed()
        assertCleared()
        store.reported(full)
        assertCleared()
        store.locationChanged("https://b.com/")
        assertCleared()
    }

    @Test fun aReportAfterARecoveredCrashIsAccepted() {
        store.reported(full)
        store.crashed()
        store.recovering()
        store.pageStarted("https://a.com/")
        assertCleared()
        store.reported(full)
        assertEquals(0xFF1A73E8.toInt(), state.statusBarColor)
    }

    @Test fun theEmptyReportClearsEverything() {
        store.reported(full)
        store.reported(PageReport.NONE)
        assertCleared()
    }
}
