package ru.digitalpunk.lumi.engine

import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Every limit of the page icon message, pinned with a literal on both sides. */
class PageIconSignalEdgeTest {
    private fun read(message: JSONObject) = pageIcons(message, true, WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT, PageEndSpacer.ID, false)
    private fun link(u: String = "https://a.com/t.png", s: Any? = 180, t: Any? = null) =
        JSONObject().put("u", u).put("k", "touch").apply { if (s != null) put("s", s); if (t != null) put("t", t) }
    private fun msg(vararg l: JSONObject) = JSONObject().put("page", "https://a.com").put("icons", JSONArray(l.toList()))
    private fun url(len: Int) = "https://a.com/" + "x".repeat(len - 14)

    @Test fun anIconUrlOf2048CharactersIsReadAndOneOf2049IsRefused() {
        assertNotNull(read(msg(link(u = url(2048)))))
        assertNull(read(msg(link(u = url(2049)))))
        assertNotNull(read(msg(link()).put("manifest", url(2048))))
        assertNull(read(msg(link()).put("manifest", url(2049))))
    }

    @Test fun aPageOriginOf2048CharactersIsReadAndOneOf2049IsRefused() {
        assertNotNull(read(msg(link()).put("page", url(2048))))
        assertNull(read(msg(link()).put("page", url(2049))))
    }

    @Test fun aSizeOf1And4096IsReadAndOf0And4097AndNegativeIsRefused() {
        assertEquals(1, read(msg(link(s = 1)))!!.links.single().size)
        assertEquals(4096, read(msg(link(s = 4096)))!!.links.single().size)
        for (bad in listOf(0, 4097, -1)) assertNull(read(msg(link(s = bad))), "size $bad")
    }

    @Test fun aTypeOf40CharactersIsReadAndOneOf41OrWithCapitalsOrSpacesIsRefused() {
        assertNotNull(read(msg(link(t = "a".repeat(40)))))
        assertNull(read(msg(link(t = "a".repeat(41)))))
        assertNull(read(msg(link(t = "Image/PNG"))))
        assertNull(read(msg(link(t = "image/png; x"))))
        assertNull(read(msg(link(t = ""))))
    }

    @Test fun sixteenLinksAreReadAndSeventeenRefused() {
        assertEquals(16, read(msg(*Array(16) { link(u = "https://a.com/$it.png") }))!!.links.size)
        assertNull(read(msg(*Array(17) { link(u = "https://a.com/$it.png") })))
    }

    @Test fun aMessageThatIsNotAnObjectIsRefusedWithoutAnException() {
        val env = WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT
        for (m in listOf<Any?>(null, "text", 7, JSONArray(), true)) assertNull(pageIcons(m, true, env, PageEndSpacer.ID, false), "$m")
    }
}
