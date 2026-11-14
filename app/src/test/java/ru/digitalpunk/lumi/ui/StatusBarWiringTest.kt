package ru.digitalpunk.lumi.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The status-bar tint is Compose and window wiring with no pure seam beyond SiteColorRules, so these read the source:
 * a band of the status-bar inset behind the status bar, off while anything covers the page, the same in private tabs.
 */
class StatusBarWiringTest {
    private val ui = File("src/main/java/ru/digitalpunk/lumi/ui")
    private val screen = File(ui, "BrowserScreen.kt").readText()
    private val band = File(ui, "StatusBarBand.kt").readText()
    private val secure = File("src/main/java/ru/digitalpunk/lumi/privacy/SecureWindow.kt").readText()

    private val tint: String get() = screen.lines().single { "val statusTint =" in it }

    @Test fun theTintIsOffWhileThePageIsCoveredOrLockedAndIsNotTiedToPrivateTabs() {
        assertTrue("if (pageCovered || lockShown) null else state.statusBarColor?.let(::Color)" in tint, tint)
        assertFalse("selectedPrivate" in tint || "style" in tint, "private tabs and Classic are tinted too")
    }

    @Test fun theBandIsDrawnFirstInTheRootBoxSoEverythingElseDrawsOverIt() {
        val root = screen.substringAfter("Box(Modifier.fillMaxSize().imePadding()) {")
        assertTrue(root.trimStart().startsWith("StatusBarBand(statusTint)"), root.take(200))
        assertEquals(1, Regex("StatusBarIcons\\(statusTint\\)").findAll(screen).count())
    }

    @Test fun theBandIsTheStatusBarInsetHighAndAnimates() {
        assertTrue("windowInsetsTopHeight(WindowInsets.statusBars)" in band)
        assertTrue("animateColorAsState(tint ?: MaterialTheme.colorScheme.surface, tween(150)" in band)
        assertTrue("isAppearanceLightStatusBars = darkIconsOn(" in band)
    }

    @Test fun theNavigationIconsFollowTheSiteBandOnlyInCompactWithAVisibleBand() {
        assertTrue(
            "style == AddressBarStyle.Compact && state.hasBottomBar && !coverOpen && !lockShown) state.bottomBarColor else null" in screen,
        )
        assertTrue("isAppearanceLightNavigationBars = darkIconsOn(" in secure)
    }

    @Test fun nothingSetsTheWindowStatusBarColour() {
        val app = File("src/main").walk().filter { it.extension == "kt" }.map { it.readText() }
        assertFalse(app.any { "statusBarColor =" in it || "setStatusBarColor" in it })
    }

    // Tester pass: pins for the lines the first tests left unpinned (each was a mutant that stayed green).
    @Test fun theStatusBarIconsAreRestoredWhenTheTintGoesAndTheEffectRestartsWithTheTint() {
        assertTrue("DisposableEffect(window, argb)" in band)
        assertTrue("onDispose { controller.isAppearanceLightStatusBars = before }" in band)
        assertTrue("val before = controller.isAppearanceLightStatusBars" in band)
    }

    @Test fun thePrivateBarKeepsItsLightIconsAndTheSiteBandWinsOverItAndBothRestartTheEffect() {
        assertTrue("darkBar = selectedPrivate && !coverOpen && !lockShown," in screen)
        val site = secure.indexOf("if (navBackground != null) controller.isAppearanceLightNavigationBars = darkIconsOn(navBackground)")
        val dark = secure.indexOf("else if (darkBar) controller.isAppearanceLightNavigationBars = false")
        assertTrue(site in 0 until dark, "the site band decides before the private bar: $site $dark")
        assertTrue("DisposableEffect(window, darkBar, navBackground)" in secure)
        assertTrue("onDispose { controller.isAppearanceLightNavigationBars = before }" in secure)
    }
}
