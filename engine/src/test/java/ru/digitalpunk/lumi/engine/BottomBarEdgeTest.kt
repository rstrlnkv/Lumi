package ru.digitalpunk.lumi.engine

import java.io.File
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inputs the bottom-bar report and its flag were not written against. */
class BottomBarEdgeTest {
    private val content = WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT
    private val ok = JSONObject().put("bottomBar", true)

    @Test fun aNullNestedOrMisspelledValueIsNotAReport() {
        assertNull(bottomBarReport(JSONObject().put("bottomBar", JSONObject.NULL), true, content, PageEndSpacer.ID))
        assertNull(bottomBarReport(JSONObject().put("bottomBar", JSONObject().put("bottomBar", true)), true, content, PageEndSpacer.ID))
        assertNull(bottomBarReport(JSONObject().put("BottomBar", true), true, content, PageEndSpacer.ID))
        assertNull(bottomBarReport(mapOf("bottomBar" to true), true, content, PageEndSpacer.ID))
    }

    @Test fun onlyTheContentScriptEnvironmentCounts() {
        for (env in listOf(0, 1, 3, -1, Int.MAX_VALUE)) {
            if (env == content) continue
            assertNull(bottomBarReport(ok, true, env, PageEndSpacer.ID), "env $env")
        }
        assertEquals(true, bottomBarReport(ok, true, content, PageEndSpacer.ID))
    }

    @Test fun theExtensionIdMustMatchExactly() {
        for (id in listOf("", " ${PageEndSpacer.ID}", PageEndSpacer.ID + " ", PageEndSpacer.ID.uppercase(), PageEndSpacer.ID + "x")) {
            assertNull(bottomBarReport(ok, true, content, id), "id '$id'")
        }
    }

    // The same flag is the state of one tab: a report, a stop and a progress update in between change nothing about it.
    @Test fun otherPageEventsLeaveTheFlagAlone() {
        val store = PageStateStore()
        store.pageStarted("https://a.com/")
        store.bottomBarChanged(true)
        store.progressChanged(50)
        store.pageStopped()
        store.canGoBackChanged(true)
        store.trackerBlocked(3)
        store.locationChanged(null)
        store.locationChanged("https://WWW.A.com:8443/other?x=1#y")
        assertTrue(store.state.value.hasBottomBar)
        assertEquals(3, store.state.value.blockedTrackers)
    }

    @Test fun aReportDoesNotTouchAnythingElseAndTwiceIsTheSameAsOnce() {
        val store = PageStateStore()
        store.pageStarted("https://a.com/")
        store.progressChanged(40)
        val before = store.state.value
        store.bottomBarChanged(true)
        store.bottomBarChanged(true)
        assertEquals(before.copy(hasBottomBar = true), store.state.value)
    }

    @Test fun reloadingTheSamePageClearsTheFlagUntilTheScriptReportsAgain() {
        val store = PageStateStore()
        store.pageStarted("https://a.com/")
        store.bottomBarChanged(true)
        store.pageStarted("https://a.com/")
        assertFalse(store.state.value.hasBottomBar)
    }

    @Test fun aReportAfterARecoveredCrashIsAccepted() {
        val store = PageStateStore()
        store.bottomBarChanged(true)
        store.crashed()
        store.recovering()
        store.pageStarted("https://a.com/")
        assertFalse(store.state.value.hasBottomBar)
        store.bottomBarChanged(true)
        assertTrue(store.state.value.hasBottomBar)
    }

    @Test fun aLocationChangeWhileCrashedToAnotherSiteKeepsTheFlagCleared() {
        val store = PageStateStore()
        store.bottomBarChanged(true)
        store.crashed()
        store.locationChanged("https://b.com/")
        assertFalse(store.state.value.hasBottomBar)
    }

    // The session's own delegate must exist before the extension reports ready: onReady binds the sessions already live
    // and bind() does nothing while `extension` is unset, so the field is set first.
    @Test fun theExtensionFieldIsSetBeforeTheReadyHookRuns() {
        val src = File("src/main/java/ru/digitalpunk/lumi/engine/PageEndExtension.kt").readText()
        val set = src.indexOf("extension = ext")
        val ready = src.indexOf("onReady()")
        assertTrue(set > 0 && ready > 0, "both lines exist")
        assertTrue(set < ready, "extension = ext must come before onReady()")
    }
}
