package ru.digitalpunk.lumi

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Site icons and suggestions go through Gecko's network stack (ADR 014), so a VPN or proxy applies as for the page.
 * Wiring only, so this reads the source.
 */
class GeckoHttpWiringTest {
    private fun src(path: String) = File("src/main/java/ru/digitalpunk/lumi/$path").readText()

    @Test fun iconsAndSuggestionsUseGeckoThroughTheEngineHolderSoNeitherStartsGecko() {
        val app = src("LumiApplication.kt")
        assertTrue("SiteIcons.create(this, appScope, GeckoHttp(engineHolder))" in app)
        assertTrue("EngineSuggestClient(GeckoHttp(engineHolder))" in app)
        assertTrue("if (!engine.isInitialized()) return null" in src("net/GeckoHttp.kt"))
    }

    @Test fun anIconFetchNeverUsesThePrivateContextAndASuggestionFollowsTheTab() {
        val http = src("net/GeckoHttp.kt")
        assertTrue("private = false" in http.substringAfter("override suspend fun get(").substringBefore("override"))
        assertTrue("client.suggest(e, q, language, private)" in src("ui/SuggestionLayer.kt"))
        assertTrue("private = selectedPrivate" in src("ui/BrowserScreen.kt"))
    }

    @Test fun startPageTilesAre64Dp() {
        val page = src("ui/startpage/StartPage.kt")
        assertTrue("SiteIconTile(tile.url, label, 64.dp" in page)
        assertTrue("Modifier.size(64.dp).background(MaterialTheme.colorScheme.tertiaryContainer" in page)
        assertFalse("56.dp" in page)
    }
}
