package ru.digitalpunk.lumi.engine

import android.app.ActivityManager
import android.content.Context
import android.view.View
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * One GeckoView runtime and a Gecko session per tab. Create it once per process (the runtime refuses a second
 * instance) and call it from the main thread. Only the selected tab and the most recently used ones keep a live
 * session (see [liveSessionCap]); the others keep their last session state and wake up when selected.
 */
class BrowserEngine(appContext: Context) {
    private class Tab(val id: TabId, val page: PageStateStore) {
        var session: GeckoSession? = null

        /** Last state from Gecko; null until the page reports one. */
        var sessionState: GeckoSession.SessionState? = null

        /** State loaded from storage that no session has parsed yet. */
        var storedState: String? = null
        var stateVersion = 0L
        var serialized: Pair<Long, String?>? = null
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

    private val runtime = GeckoRuntime.create(appContext)
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
                mutableChanges.tryEmit(Unit)
            }
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) = tab.pageChanged { pageStarted(url) }
            override fun onPageStop(session: GeckoSession, success: Boolean) = tab.pageChanged { pageStopped() }
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
            ) = tab.pageChanged(notify = true) { locationChanged(url) }

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) = tab.pageChanged { canGoBackChanged(canGoBack) }

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) = tab.pageChanged { canGoForwardChanged(canGoForward) }
        }
    }

    /** Gives [tab] a session if it has none: its saved page, scroll and history, else its URL, else a blank page. */
    private fun ensureLive(tab: Tab): GeckoSession {
        tab.session?.let { return it }
        val session = GeckoSession()
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
        live.remove(tab.id)
        tab.pageChanged { pageStopped() }
    }

    private fun enforceCap(keep: Tab) {
        live.evictions(cap, keep.id).forEach { records[it]?.let(::evict) }
    }

    /** Puts [tab] on screen: the view lets go of the old session, takes the new one and the bar numbers again. */
    private fun show(tab: Tab) {
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

    /** Opens a blank tab right after the selected one and selects it. */
    fun newTab(): TabId {
        val tab = Tab(TabId.random(), PageStateStore())
        records[tab.id] = tab
        cap = baseCap
        selected?.session?.setActive(false)
        tabsStore.add(tab.id)
        show(tab)
        mutableChanges.tryEmit(Unit)
        return tab.id
    }

    fun selectTab(id: TabId) {
        val tab = records[id] ?: return
        if (tab === selected) return
        cap = baseCap
        selected?.session?.setActive(false)
        tabsStore.select(id)
        show(tab)
        mutableChanges.tryEmit(Unit)
    }

    /** Selects the tab [offset] places away; false (nothing happens) at either end. */
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
        if (wasLast) newTab() else selected?.takeIf { wasSelected }?.let(::show)
        mutableChanges.tryEmit(Unit)
        return wasLast
    }

    /** Fills an engine that has no tabs yet. Only the selected tab gets a session now. */
    fun restoreTabs(saved: List<SavedTab>, selected: TabId?) {
        if (saved.isEmpty() || tabs.value.tabs.isNotEmpty()) return
        val safe = saved.map { it.sanitized() }
        safe.forEach {
            records[it.id] = Tab(it.id, PageStateStore(PageState(url = it.url))).apply {
                storedState = it.sessionState
                serialized = stateVersion to it.sessionState
            }
        }
        tabsStore.restore(safe.map { RestoredTab(it.id, it.url, it.title) }, selected)
        this.selected?.let(::show)
    }

    /** Plain data for storage. Only tabs whose session state changed are serialized again. */
    fun saveSnapshot(): TabsSave {
        val list = tabs.value
        val saved = list.tabs.map { info ->
            val tab = records.getValue(info.id)
            val cached = tab.serialized
            val state = if (cached != null && cached.first == tab.stateVersion) {
                cached.second
            } else {
                tab.sessionState?.toString().also { tab.serialized = tab.stateVersion to it }
            }
            SavedTab(info.id, info.url, info.title, state)
        }
        return TabsSave(saved, list.selectedId, list.tabs.associate { it.id to records.getValue(it.id).stateVersion })
    }

    /** [level] is a `ComponentCallbacks2.TRIM_MEMORY_*` value. The cap returns to normal on the next tab switch. */
    fun trimMemory(level: Int) {
        cap = capForTrim(baseCap, level)
        selected?.let(::enforceCap)
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

    fun goBack() {
        selected?.session?.goBack()
    }

    fun goForward() {
        selected?.session?.goForward()
    }

    /**
     * Height in px, measured from the screen bottom, that the address bar covers when fully shown.
     * Gecko lays out `dvh`/`svh` and bottom-fixed elements against it. Call when it changes.
     */
    fun setBarMaxHeight(px: Int) {
        barMaxHeight = px
        view?.let {
            it.setDynamicToolbarMaxHeight(px)
            // Gecko resets the clipping when the maximum changes.
            it.setVerticalClipping(barVisibleHeight)
        }
    }

    /** Height in px, from the screen bottom, that the address bar covers right now (at most [setBarMaxHeight]). */
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
    }

    fun releaseView(view: View) {
        (view as LumiGeckoView).also {
            it.listener = null
            it.releaseSession()
        }
        if (this.view === view) this.view = null
    }
}
