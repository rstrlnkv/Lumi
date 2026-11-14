package ru.digitalpunk.lumi.engine

import android.content.res.Configuration
import org.mozilla.geckoview.GeckoRuntime
import kotlin.test.Test

/** The engine forwards theme switches through `configurationChanged`; this fails on an upgrade that drops it. */
class GeckoConfigurationApiTest {
    @Test fun theRuntimeTakesAConfigurationChange() {
        GeckoRuntime::class.java.getMethod("configurationChanged", Configuration::class.java)
    }

    @Test fun theEngineForwardsAConfigurationChangeToTheRuntime() {
        BrowserEngine::class.java.getMethod("configurationChanged", Configuration::class.java)
    }
}
