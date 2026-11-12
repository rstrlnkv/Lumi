package ru.digitalpunk.lumi

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.data.tabs.TabRepository
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.tabs.TabPersistence

class LumiApplication : Application() {
    private val engineHolder = lazy { BrowserEngine(this) }

    /** Process scope: GeckoRuntime allows one instance, and rotation must not reload the page. */
    val engine by engineHolder

    val settings by lazy { SettingsStore(this) }

    val tabPersistence by lazy {
        TabPersistence(engine, TabRepository.create(this), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Do not start Gecko just to tell it that memory is short.
        if (engineHolder.isInitialized()) engine.trimMemory(level)
    }
}
