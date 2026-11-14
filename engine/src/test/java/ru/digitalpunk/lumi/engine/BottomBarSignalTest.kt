package ru.digitalpunk.lumi.engine

import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The page's bottom-bar report: one boolean from our own content script in the top frame, nothing else. */
class BottomBarSignalTest {
    private val content = WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT

    private fun report(message: Any?, topLevel: Boolean = true, env: Int = content, id: String? = PageEndSpacer.ID) =
        bottomBarReport(message, topLevel, env, id)

    @Test fun aBooleanFromOurContentScriptIsTheReport() {
        assertEquals(true, report(JSONObject().put("bottomBar", true)))
        assertEquals(false, report(JSONObject().put("bottomBar", false)))
    }

    @Test fun aMessageOfAnyOtherShapeIsIgnored() {
        assertNull(report(JSONObject()))
        assertNull(report(JSONObject().put("bottomBar", "true")))
        assertNull(report(JSONObject().put("bottomBar", 1)))
        assertNull(report(JSONObject().put("bottomBar", true).put("url", "https://a.com")))
        assertNull(report(null))
        assertNull(report("true"))
        assertNull(report(true))
        assertNull(report(1))
        assertNull(report(JSONArray().put(true)))
    }

    @Test fun aMessageFromAFrameAnExtensionPageOrAnotherExtensionIsIgnored() {
        val ok = JSONObject().put("bottomBar", true)
        assertNull(report(ok, topLevel = false))
        assertNull(report(ok, env = WebExtension.MessageSender.ENV_TYPE_EXTENSION))
        assertNull(report(ok, id = "other@example.com"))
        assertNull(report(ok, id = null))
    }
}
