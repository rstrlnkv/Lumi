package ru.digitalpunk.lumi.links

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Links to other apps (`intent:`, `tel:`, a bank's scheme) are Android wiring with no pure seam beyond the engine's
 * appLink rule (AppLinkTest), so these read the source: the person is asked first, and the intent is cut down as
 * Chrome does it, so a page can reach only a browsable activity and never Lumi's own.
 */
class AppLinkWiringTest {
    private val screen = File("src/main/java/ru/digitalpunk/lumi/ui/BrowserScreen.kt").readText()
    private val out = File("src/main/java/ru/digitalpunk/lumi/links/AppLinkOut.kt").readText()

    @Test fun theScreenAsksOnlyForTheSelectedTabAndOnlyWhenNothingCoversThePage() {
        assertTrue("engine.appLinks.collect" in screen)
        assertTrue("appLink?.takeIf { it.tab == tabsState.selectedId && !coverOpen && !lockShown }" in screen)
        assertTrue("AppLinkDialog(" in screen)
    }

    @Test fun theIntentIsBrowsableWithNoComponentSelectorOrFlagsAndNotLumi() {
        for (line in listOf(
            "Intent.parseUri(link, Intent.URI_INTENT_SCHEME)",
            "addCategory(Intent.CATEGORY_BROWSABLE)",
            "component = null",
            "selector = null",
            "flags = 0",
            "`package` != ownPackage",
        )) assertTrue(line in out, line)
    }

    @Test fun noAppFallsBackToTheIntentsWebPageElseTellsThePerson() {
        assertTrue("webLink(getStringExtra(\"browser_fallback_url\"))" in out)
        assertTrue("catch (e: ActivityNotFoundException)" in out && "catch (e: SecurityException)" in out)
        assertTrue("R.string.app_link_no_app" in screen)
    }
}
