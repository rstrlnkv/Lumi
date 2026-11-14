package ru.digitalpunk.lumi.engine

import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PageIconSignalTest {
    private val content = WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT

    private fun icons(message: Any?, topLevel: Boolean = true, env: Int = content, id: String? = PageEndSpacer.ID, private: Boolean = false) =
        pageIcons(message, topLevel, env, id, private)

    private fun link(u: String = "https://a.com/t.png", k: String = "touch", s: Any? = 180, t: Any? = null) =
        JSONObject().put("u", u).put("k", k).apply { if (s != null) put("s", s); if (t != null) put("t", t) }

    private fun msg(vararg links: JSONObject, page: Any = "https://a.com") = JSONObject().put("page", page).put("icons", JSONArray(links.toList()))

    @Test fun aValidMessageIsRead() {
        val m = msg(link(), link("https://cdn.a.com/i.svg", "icon", s = null, t = "image/svg+xml"))
            .put("manifest", "https://a.com/m.json").put("themeColor", "#336699")
        assertEquals(
            PageIcons(
                "https://a.com",
                listOf(PageIconLink("https://a.com/t.png", true, 180, null), PageIconLink("https://cdn.a.com/i.svg", false, null, "image/svg+xml")),
                "https://a.com/m.json",
                0xFF336699.toInt(),
            ),
            icons(m),
        )
    }

    @Test fun aPrivateTabOrAnotherSenderGivesNothing() {
        assertNull(icons(msg(link()), private = true))
        assertNull(icons(msg(link()), topLevel = false))
        assertNull(icons(msg(link()), env = WebExtension.MessageSender.ENV_TYPE_EXTENSION))
        assertNull(icons(msg(link()), id = "other@example.com"))
        assertNull(icons(msg(link()), id = null))
    }

    @Test fun anUnknownOrMissingKeyRejectsTheWholeMessage() {
        assertNull(icons(msg(link()).put("extra", 1)))
        assertNull(icons(JSONObject().put("icons", JSONArray())))
        assertNull(icons(JSONObject().put("page", "https://a.com")))
        assertNull(icons(msg(link().put("x", 1))))
        assertNull(icons(msg(JSONObject().put("k", "touch"))))
    }

    @Test fun anUnsafeUrlRejectsTheWholeMessage() {
        for (u in listOf("http://a.com/t.png", "https://u@a.com/t.png", "https://a.com:8443/t.png", "https://a.com\\@b.com/t.png",
            "https://a.com/t\u0001.png", "https://10.0.0.1/t.png", "https://ä.com/t.png", "data:image/png;base64,AA", "https://a.com/" + "x".repeat(2048))) {
            assertNull(icons(msg(link(), link(u))), u)
            assertNull(icons(msg(link()).put("manifest", u)), u)
        }
        assertNull(icons(msg(link(), page = "ftp://a.com")))
        assertNull(icons(msg(link(), page = "https://u@a.com")))
        assertNull(icons(msg(link(), page = 5)))
    }

    @Test fun aPortOf443IsAllowedAndAnHttpPageIsRefused() {
        assertEquals("https://a.com", icons(msg(link("https://a.com:443/t.png"), page = "https://a.com"))?.page)
        assertNull(icons(msg(link("https://a.com:443/t.png"), page = "http://a.com")))
    }

    @Test fun badSizesTypesKindsColoursOrTooManyLinksRejectTheWholeMessage() {
        for (bad in listOf(link(s = 180.5), link(s = 0), link(s = 5000), link(s = "180"), link(t = "image/png;x"), link(t = "x".repeat(41)), link(k = "manifest"), link(t = 5)))
            assertNull(icons(msg(link(), bad)), bad.toString())
        assertNull(icons(msg(link()).put("themeColor", "red")))
        assertNull(icons(msg(*Array(17) { link("https://a.com/$it.png") })))
        assertEquals(16, icons(msg(*Array(16) { link("https://a.com/$it.png") }))?.links?.size)
    }

    @Test fun aBarReportIsNotAnIconMessageAndTheReverse() {
        assertNull(icons(JSONObject().put("bottomBar", true)))
        assertNull(pageReport(msg(link()), true, content, PageEndSpacer.ID))
    }
}
