package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import kotlin.test.Test
import kotlin.test.assertEquals

/** Site icons and suggestions (ADR 014) use these members of the pinned GeckoView; this fails on an upgrade that changes them. */
class GeckoFetchApiTest {
    @Test fun theAnonymousAndPrivateFlagsKeepTheirValues() {
        assertEquals(1, GeckoWebExecutor::class.java.getField("FETCH_FLAGS_ANONYMOUS").getInt(null))
        assertEquals(8, GeckoWebExecutor::class.java.getField("FETCH_FLAGS_PRIVATE").getInt(null))
    }

    @Test fun aRequestTakesHeadersAndAPendingResultCanBeCancelled() {
        WebRequest.Builder::class.java.getMethod("header", String::class.java, String::class.java)
        assertEquals(GeckoResult::class.java, GeckoResult::class.java.getMethod("cancel").returnType)
    }

    @Test fun iconsAndSuggestionsNeverSendCookiesFollowNoRedirectAndAPrivateTabUsesThePrivateContext() {
        val own = GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS or GeckoWebExecutor.FETCH_FLAGS_NO_REDIRECTS
        assertEquals(own, fetchFlags(private = false))
        assertEquals(own or GeckoWebExecutor.FETCH_FLAGS_PRIVATE, fetchFlags(private = true))
    }
}
