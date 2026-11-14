package ru.digitalpunk.lumi.engine

import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The page's report (docs: 012-page-end-spacer-extension): `{bottomBar, bottomColor?, statusColor?}` from our own
 * content script in the top frame, colours only as "#RRGGBB"; anything else is not a report.
 */
class PageReportTest {
    private val content = WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT

    private fun report(message: Any?, topLevel: Boolean = true, env: Int = content, id: String? = PageEndSpacer.ID) =
        pageReport(message, topLevel, env, id)

    private fun bar(value: Any?) = JSONObject().put("bottomBar", value)

    @Test fun aBooleanAloneIsAReportWithoutColours() {
        assertEquals(PageReport(true), report(bar(true)))
        assertEquals(PageReport.NONE, report(bar(false)))
    }

    @Test fun theColoursAreReadAsOpaqueArgb() {
        assertEquals(
            PageReport(true, 0xFF0B57D0.toInt(), 0xFF1A73E8.toInt()),
            report(bar(true).put("bottomColor", "#0b57d0").put("statusColor", "#1A73E8")),
        )
        assertEquals(PageReport(false, null, 0xFF202124.toInt()), report(bar(false).put("statusColor", "#202124")))
        assertEquals(PageReport(true, 0xFFFFFFFF.toInt(), null), report(bar(true).put("bottomColor", "#ffffff")))
    }

    @Test fun aBottomColourWithoutABottomBarIsMalformed() =
        assertNull(report(bar(false).put("bottomColor", "#ffffff")))

    @Test fun aMalformedColourRejectsTheWholeReport() {
        assertNull(report(bar(true).put("bottomColor", "#fff")))
        assertNull(report(bar(true).put("statusColor", "red")))
        assertNull(report(bar(true).put("statusColor", JSONObject.NULL)))
        assertNull(report(bar(true).put("statusColor", 0xFF0000)))
        assertNull(report(bar(true).put("statusColor", JSONObject().put("statusColor", "#000000"))))
    }

    @Test fun anUnknownKeyOrAMissingFlagIsNotAReport() {
        assertNull(report(bar(true).put("url", "https://a.com")))
        assertNull(report(bar(true).put("BottomColor", "#000000")))
        assertNull(report(JSONObject().put("statusColor", "#000000")))
        assertNull(report(JSONObject()))
    }

    @Test fun aFlagOfAnyOtherTypeIsNotAReport() {
        for (v in listOf("true", 1, JSONObject.NULL, JSONObject().put("bottomBar", true), JSONArray().put(true))) {
            assertNull(report(bar(v)), "value $v")
        }
    }

    @Test fun aMessageThatIsNotAJsonObjectIsNotAReport() {
        for (m in listOf(null, "true", true, 1, JSONArray().put(true), mapOf("bottomBar" to true))) {
            assertNull(report(m), "message $m")
        }
    }

    @Test fun aMessageFromAFrameAnExtensionPageOrAnotherExtensionIsIgnored() {
        val ok = bar(true).put("statusColor", "#000000")
        assertNull(report(ok, topLevel = false))
        for (env in listOf(0, 1, 3, -1, Int.MAX_VALUE)) {
            if (env != content) assertNull(report(ok, env = env), "env $env")
        }
        for (id in listOf(null, "", "other@example.com", " ${PageEndSpacer.ID}", PageEndSpacer.ID + " ", PageEndSpacer.ID.uppercase())) {
            assertNull(report(ok, id = id), "id '$id'")
        }
    }

    @Test fun aSiteColourIsExactlyAHashAndSixHexDigits() {
        assertEquals(0xFF0A0B0C.toInt(), siteColor("#0a0B0c"))
        assertEquals(0xFF000000.toInt(), siteColor("#000000"))
        assertEquals(0xFFFFFFFF.toInt(), siteColor("#FFFFFF"))
        val bad = listOf(
            "#12345", "#1234567", "#GGGGGG", "#+12345", "#-12345", "#１２３４５６", "#12345١", "rgb(1,2,3)",
            "#0a0b0cff", "0a0b0c0", " #0a0b0c", "#0a0b0c ", "", "#", "#" + "0".repeat(10_000),
        )
        for (s in bad) assertNull(siteColor(s), "'$s'")
        assertNull(siteColor(null))
        assertNull(siteColor(0x0A0B0C))
        assertNull(siteColor(JSONObject.NULL))
    }
}
