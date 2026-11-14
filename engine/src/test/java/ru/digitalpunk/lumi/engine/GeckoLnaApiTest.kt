package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSession.PermissionDelegate
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.StorageController
import org.mozilla.geckoview.WebRequestError
import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals

/** Local network access (ADR 015) uses these members of the pinned GeckoView 160; this fails on an upgrade that changes them. */
class GeckoLnaApiTest {
    @Test fun theRuntimeTakesTheLnaSwitches() {
        listOf("setLnaEnabled", "setLnaBlocking").forEach {
            GeckoRuntimeSettings.Builder::class.java.getMethod(it, Boolean::class.javaObjectType)
        }
    }

    @Test fun theDelegateHasTheContentAndAndroidPermissionCalls() {
        val d = PermissionDelegate::class.java
        assertEquals(GeckoResult::class.java, d.getMethod("onContentPermissionRequest", GeckoSession::class.java, PermissionDelegate.ContentPermission::class.java).returnType)
        d.getMethod("onAndroidPermissionsRequest", GeckoSession::class.java, Array<String>::class.java, PermissionDelegate.Callback::class.java)
        PermissionDelegate.ContentPermission::class.java.getField("thirdPartyOrigin")
        PermissionDelegate.ContentPermission::class.java.getField("privateMode")
    }

    @Test fun theErrorAndLoadErrorCallExist() {
        WebRequestError::class.java.getField("ERROR_LOCAL_NETWORK_ACCESS_DENIED")
        assertEquals(GeckoResult::class.java, GeckoSession.NavigationDelegate::class.java.getMethod("onLoadError", GeckoSession::class.java, String::class.java, WebRequestError::class.java).returnType)
    }

    @Test fun theStoredPermissionsCanBeListedAndTheExecutorResolvesWithoutRedirects() {
        StorageController::class.java.getMethod("getAllPermissions")
        StorageController::class.java.getMethod("setPermission", PermissionDelegate.ContentPermission::class.java, Int::class.javaPrimitiveType)
        GeckoWebExecutor::class.java.getMethod("resolve", String::class.java).genericReturnType.typeName.let {
            assertEquals("org.mozilla.geckoview.GeckoResult<${Array<InetAddress>::class.java.typeName}>", it)
        }
        GeckoWebExecutor::class.java.getField("FETCH_FLAGS_NO_REDIRECTS")
    }

    @Test fun ownFetchesNeverFollowARedirectByThemselves() {
        assertEquals(GeckoWebExecutor.FETCH_FLAGS_NO_REDIRECTS, fetchFlags(private = false) and GeckoWebExecutor.FETCH_FLAGS_NO_REDIRECTS)
        assertEquals(GeckoWebExecutor.FETCH_FLAGS_NO_REDIRECTS, fetchFlags(private = true) and GeckoWebExecutor.FETCH_FLAGS_NO_REDIRECTS)
    }
}
