package ru.digitalpunk.lumi.engine

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.IOException
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.ContentBlocking
import org.mozilla.geckoview.GeckoResult
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings
import org.mozilla.geckoview.GeckoSession
import org.mozilla.geckoview.GeckoSessionSettings
import org.mozilla.geckoview.GeckoWebExecutor
import org.mozilla.geckoview.WebRequest
import org.mozilla.geckoview.WebResponse
import org.mozilla.geckoview.GeckoSession.PermissionDelegate.ContentPermission

/**
 * One GeckoView runtime and a Gecko session per tab. Create it once per process (the runtime refuses a second
 * instance) and call it from the main thread. Only the selected tab and the most recently used ones keep a live
 * session (see [liveSessionCap]); the others keep their last session state and wake up when selected.
 */
class BrowserEngine(appContext: Context) {
    private class Tab(val id: TabId, val page: PageStateStore, val isPrivate: Boolean = false) {
        var session: GeckoSession? = null

        /** Last state from Gecko; null until the page reports one. */
        var sessionState: GeckoSession.SessionState? = null

        /** State loaded from storage that no session has parsed yet. */
        var storedState: String? = null
        var stateVersion = 0L
        var serialized: Pair<Long, String?>? = null
        val blocked = BlockedCounter()

        /** Gecko's tracking-protection permission from the last location change of this session; its `uri` names the site. */
        var trackingPermission: ContentPermission? = null

        /** The site whose permission was last corrected on a location change and reloaded; one reload per site. */
        var correctedSite: String? = null
    }

    private val tabsStore = TabsStore()
    private val records = HashMap<TabId, Tab>()
    private val live = LiveSessions()
    private val baseCap: Int
    private var cap: Int

    /** The selected tab's page. */
    val state: StateFlow<PageState> = tabsStore.selectedPage
    val tabs: StateFlow<TabsState> = tabsStore.tabs

    private val mutableChanges = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** Fires (conflated) when the tab list, a URL, a title or a session state changed: time to save. */
    val changes: Flow<Unit> = mutableChanges

    private val mutableSnapshots = MutableSharedFlow<TabSnapshot>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** A fresh picture of a tab's page, after a load or on request ([captureSelected]). */
    val snapshots: SharedFlow<TabSnapshot> = mutableSnapshots

    private val mutableLinks = MutableSharedFlow<ContextTarget>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** A long press on an http or https link or image in the selected tab. Other links and images send nothing. */
    val linkMenu: SharedFlow<ContextTarget> = mutableLinks

    // Unlimited: a dropped offer would leak its open stream.
    private val downloadOffers = Channel<DownloadOffer>(Channel.UNLIMITED)

    /** Files pages send (a `download` link, `Content-Disposition: attachment`, a type Gecko cannot show), from any tab. */
    val downloads: Flow<DownloadOffer> = downloadOffers.receiveAsFlow()

    private val executor by lazy { GeckoWebExecutor(runtime) }

    /** Receives the visits and titles of normal tabs; read at event time, so it may be set after tabs exist. */
    var historySink: HistorySink? = null

    /**
     * While the tab grid covers the page, no automatic snapshot is taken (the page is not on screen) and the page is hidden from accessibility services.
     * Closing the grid makes the group of the tab on screen the current group again, so a new tab opens next to it.
     * The UI sets it while the Settings screen covers the page too.
     */
    var gridOpen = false
        set(value) {
            field = value
            view?.hideFromAccessibility(value)
            if (!value) tabsStore.showSelectedGroup()
        }

    /** Bumped on every tab switch; a picture that was asked for under an older value is dropped. */
    private var captureEpoch = 0

    private val handler = Handler(Looper.getMainLooper())
    private val captureAfterLoad = Runnable { selected?.let { tab -> capture(tab) {} } }

    // Strict Enhanced Tracking Protection for every session; the cookie behaviour stays Gecko's default.
    private val runtime = GeckoRuntime.create(
        appContext,
        GeckoRuntimeSettings.Builder()
            .contentBlocking(
                ContentBlocking.Settings.Builder()
                    .enhancedTrackingProtectionLevel(ContentBlocking.EtpLevel.STRICT)
                    .antiTracking(ContentBlocking.AntiTracking.STRICT)
                    .strictSocialTrackingProtection(true)
                    .build(),
            )
            .build(),
    )

    /** Sites (see [siteKey]) where the user turned protection off. The app's stored set is the source; Gecko follows it. */
    private val offHosts = HashSet<String>()
    private var view: LumiGeckoView? = null
    private var barMaxHeight = 0
    private var barVisibleHeight = 0

    /** Finger events over the page. Set it before [createView] or any time after. */
    var pageTouchListener: PageTouchListener? = null
        set(value) {
            field = value
            view?.listener = value
        }

    init {
        val manager = appContext.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
        baseCap = liveSessionCap(info.totalMem / (1024 * 1024), manager.memoryClass, manager.isLowRamDevice)
        cap = baseCap
    }

    private val selected: Tab? get() = tabs.value.selectedId?.let(records::get)

    private fun Tab.historyChanged(list: GeckoSession.HistoryDelegate.HistoryList) =
        pageChanged { historyChanged(list.map { HistoryEntry(it.title, it.uri) }, list.currentIndex) }

    private fun Tab.pageChanged(notify: Boolean = false, block: PageStateStore.() -> Unit) {
        page.block()
        tabsStore.pageChanged(id, page.state.value)
        if (notify) mutableChanges.tryEmit(Unit)
    }

    private fun attach(tab: Tab, session: GeckoSession) {
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCrash(session: GeckoSession) = tab.pageChanged { crashed() }

            // A background tab that Android killed is just evicted: it restores when selected.
            override fun onKill(session: GeckoSession) =
                if (tab === selected) tab.pageChanged { crashed() } else evict(tab)

            override fun onTitleChange(session: GeckoSession, title: String?) {
                tabsStore.titleChanged(tab.id, title.orEmpty())
                if (!tab.isPrivate && !title.isNullOrEmpty()) historySink?.titled(tab.page.state.value.url, title)
                mutableChanges.tryEmit(Unit)
            }

            override fun onContextMenu(session: GeckoSession, screenX: Int, screenY: Int, element: GeckoSession.ContentDelegate.ContextElement) {
                if (tab !== selected) return
                contextTarget(tab.id, tab.isPrivate, tab.page.state.value.url, element.linkUri, element.title, element.linkText, element.type, element.srcUri, element.altText)
                    ?.let(mutableLinks::tryEmit)
            }

            // Main thread: only read the headers here; the body is read by the app on an IO thread.
            override fun onExternalResponse(session: GeckoSession, response: WebResponse) {
                val body = response.body ?: return
                val offer = downloadOffer(response.uri, response.headers, tab.page.state.value.url, tab.isPrivate, body) ?: return
                if (downloadOffers.trySend(offer).isFailure) offer.discard()
            }
        }
        // A private session gets no onVisited at all, so it can never write global history.
        session.historyDelegate = if (tab.isPrivate) {
            object : GeckoSession.HistoryDelegate {
                override fun onHistoryStateChange(session: GeckoSession, historyList: GeckoSession.HistoryDelegate.HistoryList) = tab.historyChanged(historyList)
            }
        } else {
            object : GeckoSession.HistoryDelegate {
                override fun onHistoryStateChange(session: GeckoSession, historyList: GeckoSession.HistoryDelegate.HistoryList) = tab.historyChanged(historyList)

                override fun onVisited(session: GeckoSession, url: String, lastVisitedURL: String?, flags: Int): GeckoResult<Boolean> {
                    if (shouldReport(tab.isPrivate, flags)) historySink?.visited(url)
                    return GeckoResult.fromValue(true)
                }
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) {
                tab.blocked.reset()
                tab.pageChanged { pageStarted(url) }
            }
            override fun onPageStop(session: GeckoSession, success: Boolean) {
                tab.pageChanged { pageStopped() }
                if (tab === selected) {
                    handler.removeCallbacks(captureAfterLoad)
                    handler.postDelayed(captureAfterLoad, CAPTURE_DELAY_MILLIS)
                }
            }
            override fun onProgressChange(session: GeckoSession, progress: Int) = tab.pageChanged { progressChanged(progress) }

            override fun onSessionStateChange(session: GeckoSession, sessionState: GeckoSession.SessionState) {
                tab.sessionState = sessionState
                tab.stateVersion++
                mutableChanges.tryEmit(Unit)
            }
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) {
                tab.pageChanged(notify = true) { locationChanged(url) }
                // The store resets the count when the site changes without a page start; the counter follows it.
                if (tab.page.state.value.blockedTrackers == 0) tab.blocked.reset()
                tab.trackingPermission = perms.firstOrNull { it.permission == GeckoSession.PermissionDelegate.PERMISSION_TRACKING }
                // Gecko keeps the permission in its profile; if it disagrees with the stored set, this load did not follow
                // the set: correct it and load the page again, once per site so a permission Gecko does not keep cannot loop.
                val site = tab.trackingPermission?.let { siteKey(it.uri) }
                if (!tab.applyProtection(onlyIfChanged = true)) {
                    tab.correctedSite = null
                } else if (tab.correctedSite != site) {
                    tab.correctedSite = site
                    session.reload()
                }
            }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) = tab.pageChanged { canGoBackChanged(canGoBack) }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) = tab.pageChanged { canGoForwardChanged(canGoForward) }
        }
        session.contentBlockingDelegate = object : ContentBlocking.Delegate {
            override fun onContentBlocked(session: GeckoSession, event: ContentBlocking.BlockEvent) {
                val tracker = countsAsTracker(event.antiTrackingCategory, event.cookieBehaviorCategory, event.isBlocking)
                if (tracker && tab.blocked.add(event.uri)) tab.pageChanged { trackerBlocked(tab.blocked.count) }
            }
        }
    }

    /**
     * Sets Gecko's tracking permission to match [offHosts] for the permission's own site, not the page's URL, which can
     * already belong to the next page: allowed means protection off. True if it wrote the permission.
     */
    private fun Tab.applyProtection(onlyIfChanged: Boolean): Boolean {
        // A private tab always runs strict, with no exceptions: nothing it does reaches the stored set or Gecko's permissions.
        if (isPrivate) return false
        val permission = trackingPermission ?: return false
        val host = siteKey(permission.uri) ?: return false
        val value = if (host in offHosts) ContentPermission.VALUE_ALLOW else ContentPermission.VALUE_DENY
        if (onlyIfChanged && permission.value == value) return false
        runtime.storageController.setPermission(permission, value)
        return true
    }

    /** Gives [tab] a session if it has none: its saved page, scroll and history, else its URL, else a blank page. */
    private fun ensureLive(tab: Tab): GeckoSession {
        tab.session?.let { return it }
        val session = if (tab.isPrivate) GeckoSession(GeckoSessionSettings.Builder().usePrivateMode(true).build()) else GeckoSession()
        attach(tab, session)
        session.open(runtime)
        tab.session = session
        val restored = tab.sessionState ?: parseState(tab.storedState, GeckoSession.SessionState::fromString) { it.isEmpty() }
        tab.storedState = null
        if (restored != null) {
            tab.sessionState = restored
            session.restoreState(restored)
        } else {
            session.loadUri(tab.page.state.value.url)
        }
        return session
    }

    private fun evict(tab: Tab) {
        tab.session?.close()
        tab.session = null
        tab.trackingPermission = null
        live.remove(tab.id)
        tab.pageChanged { pageStopped() }
    }

    private fun enforceCap(keep: Tab) {
        live.evictions(cap, keep.id).forEach { records[it]?.let(::evict) }
    }

    /** Puts [tab] on screen: the view lets go of the old session, takes the new one and the bar numbers again. */
    private fun show(tab: Tab) {
        captureEpoch++
        handler.removeCallbacks(captureAfterLoad)
        view?.releaseSession()
        val session = ensureLive(tab)
        view?.let {
            it.setSession(session)
            it.setDynamicToolbarMaxHeight(barMaxHeight)
            it.setVerticalClipping(barVisibleHeight)
        }
        session.setActive(true)
        live.touch(tab.id)
        enforceCap(tab)
    }

    /**
     * Opens a tab on [url] (a blank page when it is not loadable) and selects it: right after the selected tab if that is
     * in the group, else after the group's last tab. A [private] tab goes to the private set; a normal one to the current
     * group, or the shared list when that is the private set. With [select] false the tab opens in the background, after
     * the selected tab (or the previous background tab opened from it), and gets no session until it is first selected.
     * A [fromApp] tab (a link from another app; [private] must be false) goes to the shared list and is marked [TabInfo.openedByApp].
     */
    fun newTab(private: Boolean = false, url: String = "about:blank", select: Boolean = true, fromApp: Boolean = false): TabId {
        val safeUrl = url.takeIf(::isLoadableUrl) ?: "about:blank"
        val tab = Tab(TabId.random(), PageStateStore(PageState(url = safeUrl)), private)
        records[tab.id] = tab
        if (select) {
            cap = baseCap
            selected?.session?.setActive(false)
        }
        tabsStore.add(tab.id, private, select, safeUrl, fromApp)
        if (select) show(tab)
        mutableChanges.tryEmit(Unit)
        return tab.id
    }

    /** A private tab is not selected while the private tabs are locked. */
    fun selectTab(id: TabId) {
        val tab = records[id] ?: return
        if (tab === selected || (tab.isPrivate && tabs.value.privateLocked)) return
        cap = baseCap
        selected?.session?.setActive(false)
        tabsStore.select(id)
        show(tab)
        mutableChanges.tryEmit(Unit)
    }

    /** Runs a store change and puts the newly selected tab on screen if the selection moved. */
    private fun followSelection(change: () -> Unit) {
        val before = selected
        change()
        val now = selected
        if (now !== before) {
            before?.session?.setActive(false)
            now?.let(::show)
        }
    }

    /** Hides the private tabs (on leaving the app). A private tab in front gives way to the last normal tab. */
    fun lockPrivate() = followSelection(tabsStore::lock)

    /** Shows the private tabs again, after the person unlocked; the private tab that was in front comes back. */
    fun unlockPrivate() = followSelection(tabsStore::unlock)

    /** Gives up returning to the private tab that was in front; the current group becomes the selected tab's. */
    fun leavePrivate() = tabsStore.leavePrivate()

    /** Closes every private tab and its session without saving anything. */
    fun dropPrivateTabs() {
        val ids = tabsStore.privateIds()
        if (ids.isEmpty()) return
        if (selected?.isPrivate == true) view?.releaseSession()
        ids.forEach { id ->
            records.remove(id)?.session?.close()
            live.remove(id)
        }
        val before = selected
        tabsStore.dropPrivate()
        if (selected !== before || before == null) selected?.let(::show)
        mutableChanges.tryEmit(Unit)
    }

    /** Selects the tab [offset] places away inside the selected tab's group; false (nothing happens) at either end. */
    fun switchBy(offset: Int): Boolean {
        val next = tabsStore.neighbour(offset) ?: return false
        selectTab(next)
        return true
    }

    /** True if it was the last tab and a blank one took its place. */
    fun closeTab(id: TabId): Boolean {
        val wasSelected = id == tabs.value.selectedId
        val tab = records.remove(id) ?: return false
        if (wasSelected) view?.releaseSession()
        tab.session?.close()
        live.remove(id)
        val wasLast = tabsStore.close(id)
        if (!gridOpen) tabsStore.showSelectedGroup()
        if (wasLast) newTab() else selected?.takeIf { wasSelected }?.let(::show)
        mutableChanges.tryEmit(Unit)
        return wasLast
    }

    /**
     * Shows [group] in the tab grid (null is the shared list); an unknown group is ignored. Selects no tab, except that
     * leaving the Private chip locks the private tabs and a private tab in front gives way to the last normal tab.
     */
    fun setCurrentGroup(group: GroupId?) = followSelection { tabsStore.setCurrentGroup(group) }

    /** The new group's id; null when the name is empty (after trimming) or taken, ignoring case. The current group does not change. */
    fun createGroup(name: String): GroupId? = tabsStore.createGroup(name)?.also { mutableChanges.tryEmit(Unit) }

    /** False for an unknown group, an empty name, or a name another group has. */
    fun renameGroup(id: GroupId, name: String): Boolean = tabsStore.renameGroup(id, name).also { if (it) mutableChanges.tryEmit(Unit) }

    /** Closes the group's tabs and removes it. True if a blank tab replaced everything, as for [closeTab]. */
    fun deleteGroup(id: GroupId): Boolean {
        if (tabs.value.groups.none { it.id == id }) return false
        val wasSelected = tabs.value.tabs.any { it.id == tabs.value.selectedId && it.groupId == id }
        if (wasSelected) view?.releaseSession()
        tabsStore.idsIn(id).forEach { tabId ->
            records.remove(tabId)?.session?.close()
            live.remove(tabId)
        }
        val wasLast = tabsStore.deleteGroup(id)
        if (!gridOpen) tabsStore.showSelectedGroup()
        if (wasLast) newTab() else selected?.takeIf { wasSelected }?.let(::show)
        mutableChanges.tryEmit(Unit)
        return wasLast
    }

    /**
     * Puts the tab in [group] (null is the shared list). False for an unknown tab or group, or when it is already there.
     * Sessions are untouched, and if the selected tab moves the current group stays until the grid closes.
     */
    fun moveTab(id: TabId, group: GroupId?): Boolean = tabsStore.moveTab(id, group).also { if (it) mutableChanges.tryEmit(Unit) }

    /** Fills an engine that has no tabs yet. Only the selected tab gets a session now. */
    fun restoreTabs(saved: List<SavedTab>, selected: TabId?, groups: List<TabGroup> = emptyList(), currentGroup: GroupId? = null) {
        if (saved.isEmpty() || tabs.value.tabs.isNotEmpty()) return
        val safe = saved.map { it.sanitized() }
        safe.forEach {
            records[it.id] = Tab(it.id, PageStateStore(PageState(url = it.url))).apply {
                storedState = it.sessionState
                serialized = stateVersion to it.sessionState
            }
        }
        tabsStore.restore(safe.map { RestoredTab(it.id, it.url, it.title, it.groupId) }, selected, groups, currentGroup)
        tabsStore.showSelectedGroup()
        this.selected?.let(::show)
    }

    /** Appends stored private tabs, locked and without sessions, after [restoreTabs]. Ids already in use are dropped. */
    fun restorePrivateTabs(saved: List<SavedTab>) {
        val safe = privateRestoreItems(saved, records.keys)
        safe.forEach {
            records[it.id] = Tab(it.id, PageStateStore(PageState(url = it.url)), isPrivate = true).apply {
                storedState = it.sessionState
                serialized = stateVersion to it.sessionState
            }
        }
        followSelection { tabsStore.restorePrivate(safe.map { RestoredTab(it.id, it.url, it.title, PRIVATE_GROUP) }) }
    }

    /** The session state as text; only a state that changed since the last call is serialized again. */
    private fun Tab.stateText(): String? {
        val cached = serialized
        return if (cached != null && cached.first == stateVersion) {
            cached.second
        } else {
            sessionState?.toString().also { serialized = stateVersion to it }
        }
    }

    private fun TabInfo.saved() = SavedTab(id, url, title, records.getValue(id).stateText(), groupId)

    /** Plain data for storage: normal tabs only (see [plainSave]). Only tabs whose session state changed are serialized again. */
    fun saveSnapshot(): TabsSave {
        val list = tabs.value
        val plain = plainSave(list.copy(tabs = tabsStore.allTabs()), tabsStore.lastNormal)
        val saved = plain.tabs.map { it.saved() }
        return TabsSave(saved, plain.selected, plain.tabs.associate { it.id to records.getValue(it.id).stateVersion }, list.groups, plain.currentGroup)
    }

    /** The private tabs, in order, for the encrypted store only; also while they are locked. */
    fun savePrivateSnapshot(): PrivateSave = PrivateSave(privateTabsOf(tabsStore.allTabs()).map { it.saved() })

    /** Whether the page on screen is worth a picture: a live, loaded, uncrashed normal page, and the grid is not covering it. A private tab never gets one. */
    private fun canCapture(tab: Tab): Boolean {
        val page = tab.page.state.value
        return tab === selected && !tab.isPrivate && tab.session != null && !gridOpen && !page.isCrashed && !page.url.equals("about:blank", ignoreCase = true)
    }

    /** Takes a picture of [tab] and publishes it on [snapshots]; [done] gets true if one was published. */
    private fun capture(tab: Tab, done: (Boolean) -> Unit) {
        val view = view
        if (view == null || !canCapture(tab)) return done(false)
        val epoch = captureEpoch
        view.capturePixels().accept({ raw ->
            if (raw == null) return@accept done(false)
            if (tab !== selected || epoch != captureEpoch || raw.width <= 0 || raw.height <= 0) {
                raw.recycle()
                return@accept done(false)
            }
            try {
                val crop = cropRect(raw.width, raw.height, CARD_ASPECT)
                val size = scaledSize(crop.width, crop.height, SNAPSHOT_WIDTH)
                val cropped = Bitmap.createBitmap(raw, crop.left, crop.top, crop.width, crop.height)
                val scaled = if (size.width == crop.width) cropped else Bitmap.createScaledBitmap(cropped, size.width, size.height, true)
                if (cropped !== raw && cropped !== scaled) cropped.recycle()
                if (scaled !== raw) raw.recycle()
                mutableSnapshots.tryEmit(TabSnapshot(tab.id, scaled))
                done(true)
            } catch (_: RuntimeException) {
                raw.recycle()
                done(false)
            }
        }, { done(false) })
    }

    /** A fresh snapshot of the selected tab, for the grid that is about to open. Gives up after 250 ms; the grid then shows the older one. */
    suspend fun captureSelected() {
        val tab = selected ?: return
        withTimeoutOrNull(CAPTURE_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine { c -> capture(tab) { c.resume(it) } }
        }
    }

    /**
     * Fetches [url] again for Retry or Save, through Gecko so the cookies of the right context go along; [private] uses the
     * private context. Call it from the main thread. Throws [IOException] for a non-web address or a non-2xx answer.
     */
    suspend fun fetchForDownload(url: String, private: Boolean): DownloadOffer {
        if (!isDownloadableUrl(url)) throw IOException("Not a web address")
        val flags = if (private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE
        val response = suspendCancellableCoroutine { c ->
            executor.fetch(WebRequest.Builder(url).build(), flags)
                .accept({ c.resume(it!!) }, { c.resumeWithException(it ?: IOException("Fetch failed")) })
        }
        val body = response.body
        if (response.statusCode !in 200..299 || body == null) {
            runCatching { body?.close() }
            throw IOException("HTTP ${response.statusCode}")
        }
        return downloadOffer(response.uri, response.headers, url, private, body) ?: throw IOException("Not a web address")
    }

    /** [level] is a `ComponentCallbacks2.TRIM_MEMORY_*` value. The cap returns to normal on the next tab switch. */
    fun trimMemory(level: Int) {
        cap = capForTrim(baseCap, level)
        selected?.let(::enforceCap)
    }

    /** The stored sites with protection off. Call it before [restoreTabs], so the first page already follows them. */
    fun setProtectionOffHosts(hosts: Set<String>) {
        offHosts.clear()
        hosts.mapNotNullTo(offHosts) { siteKey("https://$it") }
    }

    /**
     * Turns protection for [host] off or back on. If [tab] still shows that site, Gecko is told through it and, if it is
     * still selected, the page loads again; a tab without the permission yet is corrected on its next location change.
     * A private tab is ignored: it always runs strict and writes nothing.
     */
    fun setProtectionOff(tab: TabId, host: String, off: Boolean) {
        if (records[tab]?.isPrivate == true) return
        val key = siteKey("https://$host") ?: return
        if (off) offHosts.add(key) else offHosts.remove(key)
        val record = records[tab]?.takeIf { siteKey(it.page.state.value.url) == key } ?: return
        record.applyProtection(onlyIfChanged = false)
        if (record === selected) reload()
    }

    /** Loads web pages and the allowed `about:` pages; anything else (`javascript:`, `file:`, `about:config`) is refused. */
    fun load(url: String) {
        if (isLoadableUrl(url)) selected?.session?.loadUri(url)
    }

    /** After a crash this re-opens the session and loads the last URL; the history is lost. */
    fun reload() {
        val tab = selected ?: return
        val session = tab.session ?: return
        if (!state.value.isCrashed) return session.reload()
        val lastUrl = state.value.url
        tab.pageChanged { recovering() }
        tab.trackingPermission = null
        session.open(runtime)
        view?.let {
            it.releaseSession()
            it.setSession(session)
        }
        load(lastUrl)
    }

    fun stop() {
        selected?.session?.stop()
    }

    /** Goes to entry [index] of the selected tab's session history; only a back entry of [state]'s history is accepted. */
    fun gotoHistoryIndex(index: Int) {
        if (state.value.history.canGoTo(index)) selected?.session?.gotoHistoryIndex(index)
    }

    fun goBack() {
        selected?.session?.goBack()
    }

    fun goForward() {
        selected?.session?.goForward()
    }

    /**
     * Height in px of the part of the address bar that slides away; the page view already ends above
     * the rest of the bar. Gecko lays out `dvh`/`svh` and bottom-fixed elements against it. Call when it changes.
     */
    fun setBarMaxHeight(px: Int) {
        barMaxHeight = px
        view?.let {
            it.setDynamicToolbarMaxHeight(px)
            // Gecko resets the clipping when the maximum changes.
            it.setVerticalClipping(barVisibleHeight)
        }
    }

    /** Height in px of the sliding part that still covers the page bottom (0 to [setBarMaxHeight]). */
    fun setBarVisibleHeight(px: Int) {
        barVisibleHeight = px
        view?.setVerticalClipping(px)
    }

    /** The page. Hand it to an `AndroidView`; give it back with [releaseView]. */
    fun createView(context: Context): View = LumiGeckoView(context).also {
        selected?.let { tab -> it.setSession(ensureLive(tab)) }
        it.listener = pageTouchListener
        it.setDynamicToolbarMaxHeight(barMaxHeight)
        it.setVerticalClipping(barVisibleHeight)
        view = it
        it.hideFromAccessibility(gridOpen)
    }

    fun releaseView(view: View) {
        (view as LumiGeckoView).also {
            it.listener = null
            it.releaseSession()
        }
        if (this.view === view) this.view = null
    }
}

private const val CAPTURE_DELAY_MILLIS = 800L
private const val CAPTURE_TIMEOUT_MILLIS = 250L

/** The grid card's snapshot area: 182 x 220 dp. */
private const val CARD_ASPECT = 182f / 220f
private const val SNAPSHOT_WIDTH = 480
