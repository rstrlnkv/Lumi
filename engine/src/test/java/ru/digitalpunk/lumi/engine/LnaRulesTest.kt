package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pure rules of local network access (ADR 015), against the real GeckoView constants. */
class LnaRulesTest {
    @Test fun onlyTheTwoLocalNetworkTypesAreAskedAbout() {
        assertEquals(LnaKind.Network, lnaKind(PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS))
        assertEquals(LnaKind.Device, lnaKind(PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS))
        assertNull(lnaKind(PermissionDelegate.PERMISSION_TRACKING))
        assertNull(lnaKind(PermissionDelegate.PERMISSION_GEOLOCATION))
        assertNull(lnaKind(PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION))
    }

    @Test fun allowBlockAndDismissMapToGeckosValues() {
        assertEquals(ContentPermission.VALUE_ALLOW, lnaValue(LnaAnswer.Allow))
        assertEquals(ContentPermission.VALUE_DENY, lnaValue(LnaAnswer.Block))
        assertEquals(ContentPermission.VALUE_PROMPT, lnaValue(LnaAnswer.Dismiss))
    }

    @Test fun theMigrationResetsOnlyStoredLocalNetworkDenials() {
        val net = PermissionDelegate.PERMISSION_LOCAL_NETWORK_ACCESS
        val dev = PermissionDelegate.PERMISSION_LOCAL_DEVICE_ACCESS
        assertTrue(isStoredLnaDenial(net, ContentPermission.VALUE_DENY, privateMode = false))
        assertTrue(isStoredLnaDenial(dev, ContentPermission.VALUE_DENY, privateMode = false))
        assertFalse(isStoredLnaDenial(net, ContentPermission.VALUE_ALLOW, privateMode = false))
        assertFalse(isStoredLnaDenial(dev, ContentPermission.VALUE_PROMPT, privateMode = false))
        assertFalse(isStoredLnaDenial(net, ContentPermission.VALUE_DENY, privateMode = true))
        // The site-protection exception (ADR 005) is a tracking permission set to DENY: never touched.
        assertFalse(isStoredLnaDenial(PermissionDelegate.PERMISSION_TRACKING, ContentPermission.VALUE_DENY, privateMode = false))
        assertFalse(isStoredLnaDenial(PermissionDelegate.PERMISSION_GEOLOCATION, ContentPermission.VALUE_DENY, privateMode = false))
    }

    @Test fun thePromptNamesTheSiteAndAThirdPartyOnlyWhenItDiffers() {
        val p = lnaPrompt("https://example.com/", "https://widgets.example.net", LnaKind.Network, private = false)
        assertEquals("example.com", p.site)
        assertEquals("widgets.example.net", p.via)
        assertNull(lnaPrompt("https://example.com/", "https://example.com", LnaKind.Network, false).via)
        assertNull(lnaPrompt("https://example.com/", null, LnaKind.Device, false).via)
        assertNull(lnaPrompt("https://example.com/", "", LnaKind.Device, false).via)
        assertEquals("not a uri", lnaPrompt("not a uri", null, LnaKind.Device, false).site)
    }

    @Test fun theKeyKeepsPrivateAndNormalRequestsApart() {
        val normal = lnaPrompt("https://example.com/", null, LnaKind.Network, private = false)
        val private = lnaPrompt("https://example.com/", null, LnaKind.Network, private = true)
        assertTrue(normal.key != private.key)
        assertTrue(normal.key != lnaPrompt("https://example.com/", null, LnaKind.Device, false).key)
    }

    @Test fun aFirstPartyAndAThirdPartyFrameRequestGetTwoSheets() {
        val own = lnaPrompt("https://example.com/", null, LnaKind.Network, false)
        val framed = lnaPrompt("https://example.com/", "https://widgets.example.net", LnaKind.Network, false)
        assertTrue(own.key != framed.key)
    }

    @Test fun theSheetTextLosesFormatAndControlCharactersAndIsCapped() {
        val p = lnaPrompt("https://exa\u202Emple.com/", "https://wid\u200Bgets.net", LnaKind.Network, false)
        assertEquals("example.com", p.site)
        assertEquals("widgets.net", p.via)
        assertEquals("ab", lnaPrompt("a\u0007\u2066b", null, LnaKind.Device, false).site)
        val long = "https://" + "a".repeat(5000) + ".com/"
        assertEquals(MAX_LNA_HOST_LENGTH, lnaPrompt(long, null, LnaKind.Network, false).site.length)
        assertEquals(MAX_LNA_HOST_LENGTH, lnaPrompt("x".repeat(5000), "y".repeat(5000), LnaKind.Network, false).via?.length)
    }

    @Test fun onlyAnAskForTheLocalNetworkPermissionAloneGoesToAndroid() {
        assertTrue(asksOnlyLocalNetwork(arrayOf(ACCESS_LOCAL_NETWORK)))
        assertFalse(asksOnlyLocalNetwork(arrayOf(ACCESS_LOCAL_NETWORK, "android.permission.ACCESS_FINE_LOCATION")))
        assertFalse(asksOnlyLocalNetwork(arrayOf("android.permission.CAMERA")))
        assertFalse(asksOnlyLocalNetwork(emptyArray()))
        assertFalse(asksOnlyLocalNetwork(null))
        assertEquals("android.permission.ACCESS_LOCAL_NETWORK", ACCESS_LOCAL_NETWORK)
    }

    @Test fun anErrorPageBecomesABase64DataUrlThatKeepsEveryCharacter() {
        val html = "<p>Доступ + 100% &amp; #frag ?q</p>"
        val url = htmlDataUrl(html)
        val prefix = "data:text/html;charset=utf-8;base64,"
        assertTrue(url.startsWith(prefix))
        assertEquals(html, String(Base64.getDecoder().decode(url.removePrefix(prefix)), Charsets.UTF_8))
    }
}
