package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebMessage
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import java.io.InputStream
import kotlin.test.Test
import kotlin.test.assertEquals

/** The download handoff uses these members of the pinned GeckoView; this fails on an upgrade that changes them. */
class GeckoDownloadApiTest {
    @Test fun onExternalResponseTakesTheSessionAndAWebResponse() {
        val method = GeckoSession.ContentDelegate::class.java.getMethod("onExternalResponse", GeckoSession::class.java, WebResponse::class.java)
        assertEquals(Void.TYPE, method.returnType)
    }

    @Test fun aWebResponseCarriesUriHeadersStatusAndABodyStream() {
        assertEquals(String::class.java, WebMessage::class.java.getField("uri").type)
        assertEquals(Map::class.java, WebMessage::class.java.getField("headers").type)
        assertEquals(Int::class.javaPrimitiveType, WebResponse::class.java.getField("statusCode").type)
        assertEquals(InputStream::class.java, WebResponse::class.java.getField("body").type)
    }

    @Test fun theExecutorFetchesWithFlagsAndHasAPrivateFlag() {
        GeckoWebExecutor::class.java.getConstructor(GeckoRuntime::class.java)
        assertEquals(GeckoResult::class.java, GeckoWebExecutor::class.java.getMethod("fetch", WebRequest::class.java, Int::class.javaPrimitiveType).returnType)
        GeckoWebExecutor::class.java.getField("FETCH_FLAGS_PRIVATE")
        WebRequest.Builder::class.java.getConstructor(String::class.java)
    }
}
