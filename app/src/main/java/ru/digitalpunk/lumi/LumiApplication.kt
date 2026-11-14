package ru.digitalpunk.lumi

import android.app.Application
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.data.downloads.DownloadRepository
import ru.digitalpunk.lumi.data.downloads.DownloadStorage
import ru.digitalpunk.lumi.data.icons.SiteIcons
import ru.digitalpunk.lumi.data.library.LibraryRepository
import ru.digitalpunk.lumi.data.privatetabs.PrivateTabStore
import ru.digitalpunk.lumi.data.search.EngineSuggestClient
import ru.digitalpunk.lumi.data.tabs.SnapshotStore
import ru.digitalpunk.lumi.data.tabs.TabRepository
import ru.digitalpunk.lumi.downloads.DownloadAsks
import ru.digitalpunk.lumi.downloads.DownloadManager
import ru.digitalpunk.lumi.downloads.DownloadNotifier
import ru.digitalpunk.lumi.defaultbrowser.DefaultBrowser
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.links.AppLinkInbox
import ru.digitalpunk.lumi.library.HistoryRecorder
import ru.digitalpunk.lumi.privacy.PrivateGate
import ru.digitalpunk.lumi.privacy.PrivatePersistence
import ru.digitalpunk.lumi.tabs.TabPersistence
import ru.digitalpunk.lumi.tabs.TabSnapshots
import ru.digitalpunk.lumi.updates.UpdateChecker
import ru.digitalpunk.lumi.updates.UpdateNotifier
import ru.digitalpunk.lumi.updates.UpdateSchedule

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

    /** Bookmarks and history, in the same `tabs.db` as the tabs. */
    val library by lazy { LibraryRepository.create(this, siteIcons) }

    /** Normal tabs' visits go to [library]; private tabs never report any. */
    val historyRecorder by lazy { HistoryRecorder(library, appScope).also { engine.historySink = it } }

    /** Site icons for bookmarks and history, fetched outside Gecko (ADR 008). */
    val siteIcons by lazy { SiteIcons.create(this, appScope) }

    /** Search suggestions from the chosen engine (ADR 009); its own pinned client, so its live limit is its own. */
    val suggestClient by lazy { EngineSuggestClient() }

    val downloadNotifier by lazy { DownloadNotifier(this) }

    /** Every download of the process (ADR 010); the copy outlives the activity. */
    val downloads by lazy { DownloadManager(this, engine, DownloadRepository.create(this), DownloadStorage(this), downloadNotifier, appScope) }

    /** Files waiting for the ask sheet, and the permission requests of a "Download" tap. */
    val downloadAsks by lazy { DownloadAsks(this, engine, downloads, settings, appScope) }

    /** Set by a download notification or a "Show" action: the Library opens on the Downloads tab. */
    val showDownloads = MutableStateFlow(false)

    /** Links and web searches from other apps, waiting for the restored tabs. */
    val appLinks = AppLinkInbox()

    /** Whether Lumi is the default browser, and the request for it; the Settings row and the start-page card share it. */
    val defaultBrowser by lazy { DefaultBrowser(this, settings, appScope) }

    /** The update check against GitHub Releases: the daily job and "Check now" in About. */
    val updates by lazy { UpdateChecker(settings, UpdateNotifier(this)) }

    override fun onCreate() {
        super.onCreate()
        // The daily job follows the Settings switch; Dispatchers.Default keeps the scheduler's binder calls off the main thread.
        appScope.launch(Dispatchers.Default) { settings.updatesOn.distinctUntilChanged().collect { UpdateSchedule.sync(this@LumiApplication, it) } }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Do not start Gecko just to tell it that memory is short.
        if (engineHolder.isInitialized()) engine.trimMemory(level)
    }

    // A theme switch recreates the activity, not the process: Gecko keeps the old scheme unless it is told.
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (engineHolder.isInitialized()) engine.configurationChanged(newConfig)
    }
}
