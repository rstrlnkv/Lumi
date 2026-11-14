package ru.digitalpunk.lumi.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Source pins for what needs a live GeckoRuntime to run: one install, private tabs allowed, a listener that goes away. */
class PageEndWiringTest {
    private val main = File("src/main/java/ru/digitalpunk/lumi/engine")
    private val extension = File(main, "PageEndExtension.kt").readText()
    private val engine = File(main, "BrowserEngine.kt").readText()

    @Test fun theEngineCreatesAndStartsTheExtensionExactlyOnce() {
        assertEquals(1, Regex("PageEndExtension\\(").findAll(engine).count())
        assertEquals(1, Regex("pageEnd[^\\n]*\\.start\\(\\)|\\.also \\{ it\\.start\\(\\) \\}").findAll(engine).count())
    }

    @Test fun theExtensionIsInstalledFromTheBundledUriWithItsId() =
        assertTrue(extension.contains("ensureBuiltIn(PageEndSpacer.URI, PageEndSpacer.ID)"))

    @Test fun privateBrowsingIsAllowedUnlessAlreadyAllowed() {
        assertTrue(extension.contains("setAllowedInPrivateBrowsing(ext, true)"))
        assertTrue(extension.contains("if (ext.metaData.allowedInPrivateBrowsing)"))
    }

    @Test fun theLayoutListenerAddedInCreateViewIsRemovedInReleaseView() {
        assertEquals(1, Regex("addOnLayoutChangeListener\\(pageEndWidth\\)").findAll(engine).count())
        assertEquals(1, Regex("removeOnLayoutChangeListener\\(pageEndWidth\\)").findAll(engine).count())
    }

    // Every live session, private included, gets the page-report delegate; sessions opened before the extension was
    // ready get it when it is.
    // An evicted tab has no page that can report, so its report is cleared with it.
    @Test fun anEvictedTabForgetsItsReport() =
        assertTrue(engine.substringAfter("private fun evict(").contains("tab.pageChanged { pageStopped(); reported(PageReport.NONE) }"))

    // The session's own delegate must exist before the extension reports ready: onReady binds the sessions already live
    // and bind() does nothing while `extension` is unset, so the field is set first.
    @Test fun theExtensionFieldIsSetBeforeTheReadyHookRuns() {
        val set = extension.indexOf("extension = ext")
        val ready = extension.indexOf("onReady()")
        assertTrue(set > 0 && ready > 0, "both lines exist")
        assertTrue(set < ready, "extension = ext must come before onReady()")
    }

    @Test fun everyLiveSessionIsBoundToTheBottomBarReport() {
        assertTrue(extension.contains("session.webExtensionController.setMessageDelegate("))
        assertEquals(1, Regex("bindPageEnd\\(tab, session\\)").findAll(engine.substringAfter("private fun ensureLive(")
            .substringBefore("private fun evict(")).count())
        assertTrue(Regex("PageEndExtension\\(runtime\\) \\{[^}]*records\\.values").containsMatchIn(engine))
        assertTrue(engine.contains("pageEnd.bind(session) { tab.pageChanged { reported(it) } }"))
    }
}
