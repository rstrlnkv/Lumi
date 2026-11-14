package ru.digitalpunk.lumi.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/** The More menu is Compose wiring, so this reads the source. */
class MoreMenuWiringTest {
    private val controls = File("src/main/java/ru/digitalpunk/lumi/ui/BarControls.kt").readText()
    private val screen = File("src/main/java/ru/digitalpunk/lumi/ui/BrowserScreen.kt").readText()

    @Test fun `Downloads follows Library in the More menu and opens the Downloads tab`() {
        val library = controls.indexOf("R.string.menu_library")
        val downloads = controls.indexOf("R.string.menu_downloads")
        assertTrue(library >= 0 && downloads > library, "library $library, downloads $downloads")
        assertTrue("library.openDownloads()" in controls)
        assertTrue("openDownloads = { showDownloads.value = true }" in screen)
    }
}
