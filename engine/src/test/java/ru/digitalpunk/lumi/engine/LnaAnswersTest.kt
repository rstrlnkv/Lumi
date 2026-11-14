package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import kotlin.test.Test
import kotlin.test.assertEquals

class LnaAnswersTest {
    private val net = PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS
    private val device = PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS
    private val allow = ContentPermission.VALUE_ALLOW
    private val deny = ContentPermission.VALUE_DENY

    private fun entry(uri: String, permission: Int = net, value: Int = allow, private: Boolean = false, via: String? = null) =
        LnaEntry(uri, via, permission, value, private)

    @Test fun `private answers are left out`() {
        assertEquals(emptyList(), lnaSites(listOf(entry("https://a.com", private = true))))
    }

    @Test fun `prompt values are left out`() {
        assertEquals(emptyList(), lnaSites(listOf(entry("https://a.com", value = ContentPermission.VALUE_PROMPT))))
    }

    @Test fun `other permissions are left out`() {
        assertEquals(emptyList(), lnaSites(listOf(entry("https://a.com", permission = PermissionDelegate.PERMISSION_GEOLOCATION))))
    }

    @Test fun `both kinds of one site are one row`() {
        val sites = lnaSites(listOf(entry("https://a.com", net), entry("https://a.com", device)))
        assertEquals(listOf(LnaSite("a.com", null, allowed = true)), sites)
    }

    @Test fun `one denial makes the site blocked`() {
        val sites = lnaSites(listOf(entry("https://a.com", net, allow), entry("https://a.com", device, deny)))
        assertEquals(listOf(LnaSite("a.com", null, allowed = false)), sites)
    }

    @Test fun `a third-party frame keeps its via`() {
        val sites = lnaSites(listOf(entry("https://plex.tv", via = "https://app.example.com"), entry("https://b.org", value = deny)))
        assertEquals(listOf(LnaSite("b.org", null, allowed = false), LnaSite("plex.tv", "app.example.com", allowed = true)), sites)
    }
}
