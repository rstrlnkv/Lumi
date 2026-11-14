package ru.digitalpunk.lumi.engine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The built-in extension's files: the id the engine installs, the files it lists, and nothing that sends data. */
class PageEndAssetsTest {
    private val dir = File("src/main/assets/" + PageEndSpacer.URI.removePrefix("resource://android/assets/"))
    private val manifest = File(dir, "manifest.json").readText()

    @Test fun theManifestIdIsTheOneTheEngineInstalls() =
        assertTrue(manifest.contains("\"id\": \"${PageEndSpacer.ID}\""))

    @Test fun theBackgroundScriptConnectsToTheNativeAppTheEngineListensOn() =
        assertTrue(File(dir, "background.js").readText().contains("connectNative(\"${PageEndSpacer.NATIVE_APP}\")"))

    @Test fun everyFileTheManifestListsExists() {
        val files = Regex("\"([\\w-]+\\.(?:js|css))\"").findAll(manifest).map { it.groupValues[1] }.toList()
        assertEquals(listOf("background.js", "spacer.css", "content.js"), files)
        files.forEach { assertTrue(File(dir, it).isFile, it) }
    }

    // nativeMessagingFromContent lets the content script send its one bottom-bar boolean to the app.
    @Test fun permissionsAreOnlyNativeMessagingAndStorage() =
        assertTrue(manifest.contains("\"permissions\": [\"geckoViewAddons\", \"nativeMessaging\", \"nativeMessagingFromContent\", \"storage\"]"))

    // Gecko keeps an installed built-in extension until its version changes.
    @Test fun theVersionIsRaisedForTheIconLinksAndTheTranslucentBarRule() = assertTrue(manifest.contains("\"version\": \"1.9\""))

    // Lumi refuses file: pages (UrlGuard), so the extension does not ask for them.
    @Test fun onlyTopFramesOfWebPagesAreMatched() {
        assertTrue(manifest.contains("\"matches\": [\"http://*/*\", \"https://*/*\"]"))
        assertTrue(manifest.contains("\"all_frames\": false"))
    }

    @Test fun noScriptSendsAnythingOverTheNetworkOrToThePage() {
        dir.listFiles { f -> f.extension == "js" }!!.forEach { f ->
            val js = f.readText()
            listOf("fetch(", "XMLHttpRequest", "sendBeacon", "WebSocket", "postMessage").forEach {
                assertFalse(js.contains(it), "${f.name} uses $it")
            }
        }
    }
}
