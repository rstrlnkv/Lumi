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
}
