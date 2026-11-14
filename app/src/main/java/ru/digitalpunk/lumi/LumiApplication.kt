package ru.digitalpunk.lumi

import android.app.Application
import android.content.res.Configuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.data.ThemeChoice
import ru.digitalpunk.lumi.engine.PageTheme
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.data.downloads.DownloadRepository
import ru.digitalpunk.lumi.data.downloads.DownloadStorage
import ru.digitalpunk.lumi.data.icons.SiteIcons
import ru.digitalpunk.lumi.data.icons.bookmarkIconHosts
import ru.digitalpunk.lumi.net.pageIconOffer
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
import ru.digitalpunk.lumi.net.GeckoHttp
import ru.digitalpunk.lumi.net.LocalNetworkAsks
import ru.digitalpunk.lumi.net.lnaErrorHtml
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
    private val engineHolder = lazy {
        BrowserEngine(this).also {
            it.localNetworkAsker = localNetworkAsks::ask
            it.pageIconListener = { icons -> siteIcons.offerPageLinks(pageIconOffer(icons)) }
            it.lnaErrorPage = { uri -> lnaErrorHtml(getString(R.string.lna_error_title), getString(R.string.lna_error_body), getString(R.string.lna_error_retry), uri) }
        }
    }

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
    val library by lazy { LibraryRepository.create(this, siteIcons, settings) }

    /** Normal tabs' visits go to [library]; private tabs never report any. */
    val historyRecorder by lazy { HistoryRecorder(library, appScope).also { engine.historySink = it } }

    /** Site icons for bookmarks and history, fetched through Gecko without cookies (ADR 014). */
    val siteIcons: SiteIcons by lazy {
        SiteIcons.create(this, appScope, GeckoHttp(engineHolder)).also { icons ->
            // Off the main thread: the library is made with these icons, so it is read once they exist.
            appScope.launch(Dispatchers.Default) { library.nodes.collect { nodes -> icons.wantedHosts = bookmarkIconHosts(nodes.map { n -> n.url }) } }
        }
    }

    /** Search suggestions from the chosen engine (ADR 009), fetched through Gecko (ADR 014). */
    val suggestClient by lazy { EngineSuggestClient(GeckoHttp(engineHolder)) }

    val downloadNotifier by lazy { DownloadNotifier(this) }

    /** Every download of the process (ADR 010); the copy outlives the activity. */
    val downloads by lazy { DownloadManager(this, engine, DownloadRepository.create(this), DownloadStorage(this), downloadNotifier, appScope) }

    /** Files waiting for the ask sheet, and the permission requests of a "Download" tap. */
    val downloadAsks by lazy { DownloadAsks(this, engine, downloads, settings, appScope) }

    /** Gecko's asks for the Android 17 local network permission (ADR 015). */
    val localNetworkAsks by lazy { LocalNetworkAsks(this) }

    /** Once per install: the local network denials GeckoView stored before Lumi asked become askable again (ADR 015). */
    fun resetLnaDenialsOnce() {
        appScope.launch {
            if (!settings.lnaDenialsReset.first()) engine.resetLnaDenials { appScope.launch { settings.setLnaDenialsReset() } }
        }
    }

    /** Set by a download notification or a "Show" action: the Library opens on the Downloads tab. */
    val showDownloads = MutableStateFlow(false)

    /** Links and web searches from other apps, waiting for the restored tabs. */
    val appLinks = AppLinkInbox()

    /** Whether Lumi is the default browser, and the request for it; the Settings row and the start-page card share it. */
    val defaultBrowser by lazy { DefaultBrowser(this, settings, appScope) }

    /** The update check against GitHub Releases: the daily job and "Check now" in About. */
    val updates by lazy { UpdateChecker(settings, UpdateNotifier(this)) }

    private var followingUpdateSwitch = false

    /**
     * The daily job follows the Settings switch, from the first activity on; not from `onCreate`, which also runs in every
     * Gecko child process. The persisted job outlives the process; Dispatchers.Default keeps binder calls off the main thread.
     */
    fun followUpdateSwitch() {
        if (followingUpdateSwitch) return
        followingUpdateSwitch = true
        appScope.launch(Dispatchers.Default) {
            settings.updatesOn.distinctUntilChanged().collect { on -> runCatching { UpdateSchedule.sync(this@LumiApplication, on) } }
        }
    }

    private var followingTheme = false

    /** Pages follow Lumi's theme; from the first activity on, like [followUpdateSwitch]. */
    fun followTheme() {
        if (followingTheme) return
        followingTheme = true
        appScope.launch {
            settings.theme.distinctUntilChanged().collect { theme ->
                engine.setPageTheme(
                    when (theme) {
                        ThemeChoice.System -> PageTheme.System
                        ThemeChoice.Light -> PageTheme.Light
                        ThemeChoice.Dark -> PageTheme.Dark
                    },
                )
            }
        }
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
