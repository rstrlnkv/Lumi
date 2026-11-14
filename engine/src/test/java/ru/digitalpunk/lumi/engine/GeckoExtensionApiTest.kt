package ru.digitalpunk.lumi.engine

import org.json.JSONObject
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.WebExtension
import org.mozilla.geckoview.WebExtensionController
import kotlin.test.Test
import kotlin.test.assertEquals

/** The page-end extension uses these GeckoView members; this fails on an upgrade that changes their shape. */
class GeckoExtensionApiTest {
    @Test fun ensureBuiltInTakesUriAndIdAndReturnsAResult() {
        val method = WebExtensionController::class.java.getMethod("ensureBuiltIn", String::class.java, String::class.java)
        assertEquals(GeckoResult::class.java, method.returnType)
    }

    @Test fun privateBrowsingCanBeAllowedForAnExtension() {
        WebExtensionController::class.java.getMethod(
            "setAllowedInPrivateBrowsing", WebExtension::class.java, Boolean::class.javaPrimitiveType,
        )
        WebExtension.MetaData::class.java.getField("allowedInPrivateBrowsing")
    }

    @Test fun anExtensionTakesAMessageDelegateForANativeApp() {
        WebExtension::class.java.getMethod("setMessageDelegate", WebExtension.MessageDelegate::class.java, String::class.java)
        WebExtension.MessageDelegate::class.java.getMethod("onConnect", WebExtension.Port::class.java)
    }

    @Test fun aPortPostsJsonAndReportsItsDisconnect() {
        WebExtension.Port::class.java.getMethod("postMessage", JSONObject::class.java)
        WebExtension.Port::class.java.getMethod("setDelegate", WebExtension.PortDelegate::class.java)
        WebExtension.PortDelegate::class.java.getMethod("onDisconnect", WebExtension.Port::class.java)
    }

    // The content script's bottom-bar report arrives per session (docs: 012-page-end-spacer-extension).
    @Test fun aSessionTakesAMessageDelegateForOurExtension() {
        val controller = GeckoSession::class.java.getMethod("getWebExtensionController").returnType
        assertEquals(WebExtension.SessionController::class.java, controller)
        controller.getMethod("setMessageDelegate", WebExtension::class.java, WebExtension.MessageDelegate::class.java, String::class.java)
        val onMessage = WebExtension.MessageDelegate::class.java
            .getMethod("onMessage", String::class.java, Any::class.java, WebExtension.MessageSender::class.java)
        assertEquals(GeckoResult::class.java, onMessage.returnType)
    }

    @Test fun aSenderNamesItsFrameItsEnvironmentAndItsExtension() {
        val sender = WebExtension.MessageSender::class.java
        assertEquals(Boolean::class.javaPrimitiveType, sender.getMethod("isTopLevel").returnType)
        assertEquals(Int::class.javaPrimitiveType, sender.getField("environmentType").type)
        assertEquals(WebExtension::class.java, sender.getField("webExtension").type)
        assertEquals(2, sender.getField("ENV_TYPE_CONTENT_SCRIPT").getInt(null))
        assertEquals(String::class.java, WebExtension::class.java.getField("id").type)
    }
}
