package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.AllowOrDeny
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoSession
import kotlin.test.Test
import kotlin.test.assertEquals

/** The engine stops app links in these GeckoView members; this fails on an upgrade that renames or removes one. */
class GeckoLoadRequestApiTest {
    @Test fun navigationDelegateDecidesTopLevelLoads() {
        val m = GeckoSession.NavigationDelegate::class.java.getMethod(
            "onLoadRequest",
            GeckoSession::class.java,
            GeckoSession.NavigationDelegate.LoadRequest::class.java,
        )
        assertEquals(GeckoResult::class.java, m.returnType)
        assertEquals(String::class.java, GeckoSession.NavigationDelegate.LoadRequest::class.java.getField("uri").type)
        AllowOrDeny.valueOf("DENY")
    }
}
