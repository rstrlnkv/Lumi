package ru.digitalpunk.lumi

import android.app.Application
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.engine.BrowserEngine

class LumiApplication : Application() {
    /** Process scope: GeckoRuntime allows one instance, and rotation must not reload the page. */
    val engine by lazy { BrowserEngine(this) }

    val settings by lazy { SettingsStore(this) }
}
