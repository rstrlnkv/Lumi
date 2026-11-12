package ru.digitalpunk.lumi

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.data.privatetabs.PrivateTabStore
import ru.digitalpunk.lumi.data.tabs.SnapshotStore
import ru.digitalpunk.lumi.data.tabs.TabRepository
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.privacy.PrivateGate
import ru.digitalpunk.lumi.privacy.PrivatePersistence
import ru.digitalpunk.lumi.tabs.TabPersistence
import ru.digitalpunk.lumi.tabs.TabSnapshots

class LumiApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engineHolder = lazy { BrowserEngine(this) }

    /** Process scope: GeckoRuntime allows one instance, and rotation must not reload the page. */
    val engine by engineHolder

    val settings by lazy { SettingsStore(this) }

    val tabPersistence by lazy {
        TabPersistence(engine, TabRepository.create(this), settings, appScope)
    }

    /** The private tabs' lock, for the process: a new activity after rotation keeps it. */
    val privateGate by lazy { PrivateGate(engine, this) }

    val privatePersistence by lazy { PrivatePersistence(engine, PrivateTabStore.create(this), privateGate, appScope) }

    val tabSnapshots by lazy { TabSnapshots(engine, SnapshotStore.create(this), appScope) }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Do not start Gecko just to tell it that memory is short.
        if (engineHolder.isInitialized()) engine.trimMemory(level)
    }
}
