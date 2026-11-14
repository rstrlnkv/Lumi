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
    }

    @Test fun `the theme's bar style is set before the screen's own overrides run`() {
        // A DisposableEffect runs in composition order, before the band and private-bar effects under it;
        // a LaunchedEffect would run after them and overwrite their icons.
        val effect = activity.indexOf("DisposableEffect(dark)")
        assertTrue(effect >= 0 && "LaunchedEffect(dark)" !in activity)
        assertTrue(activity.indexOf("enableEdgeToEdge(", effect) in effect until activity.indexOf("Surface(Modifier.fillMaxSize())"))
    }

    @Test fun `bar icon overrides give back Lumi's theme, not the value they found`() {
        val band = File(main, "ru/digitalpunk/lumi/ui/StatusBarBand.kt").readText()
        val secure = File(main, "ru/digitalpunk/lumi/privacy/SecureWindow.kt").readText()
        assertTrue("onDispose { controller.isAppearanceLightStatusBars = lightIcons }" in band)
        assertTrue("onDispose { controller.isAppearanceLightNavigationBars = lightIcons }" in secure)
        listOf(band, secure).forEach { assertTrue("val before" !in it && "!LocalDarkTheme.current" in it) }
    }

    @Test fun `only LumiTheme reads the system dark setting`() {
        val readers = main.walk().filter { it.extension == "kt" && "isSystemInDarkTheme()" in it.readText() }.map { it.name }.toList()
        assertEquals(listOf("LumiTheme.kt"), readers)
    }
}
