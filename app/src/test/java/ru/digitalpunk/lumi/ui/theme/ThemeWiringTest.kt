package ru.digitalpunk.lumi.ui.theme

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The theme reaches the window through Compose and activity wiring with no pure seam, so these read the source. */
class ThemeWiringTest {
    private val main = File("src/main/java")
    private val activity = File(main, "ru/digitalpunk/lumi/MainActivity.kt").readText()

    @Test fun `the activity passes the stored theme and wallpaper switch to LumiTheme`() {
        assertTrue("LumiTheme(theme, wallpaperColors)" in activity)
        assertTrue("settings.theme.collectAsStateWithLifecycle" in activity)
        assertTrue("settings.wallpaperColors.collectAsStateWithLifecycle" in activity)
    }

    @Test fun `the system bars follow Lumi's theme, not the resources`() {
        assertTrue("SystemBarStyle.auto(" in activity)
        assertTrue("{ dark }" in activity)
        assertTrue("LaunchedEffect(dark)" in activity)
    }

    @Test fun `only LumiTheme reads the system dark setting`() {
        val readers = main.walk().filter { it.extension == "kt" && "isSystemInDarkTheme()" in it.readText() }.map { it.name }.toList()
        assertEquals(listOf("LumiTheme.kt"), readers)
    }
}
