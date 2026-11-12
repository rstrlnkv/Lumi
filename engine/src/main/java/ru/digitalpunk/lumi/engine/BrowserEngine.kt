package ru.digitalpunk.lumi.engine

import android.content.Context
import android.view.View
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoSession

/**
 * One GeckoView runtime and one session. Create it once per process (the runtime refuses a second
 * instance) and call it from the main thread.
 */
class BrowserEngine(appContext: Context) {
    private val store = PageStateStore()
    val state: StateFlow<PageState> = store.state

    private val runtime = GeckoRuntime.create(appContext)
    private val session = GeckoSession()
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
        session.contentDelegate = object : GeckoSession.ContentDelegate {
            override fun onCrash(session: GeckoSession) = store.crashed()
            override fun onKill(session: GeckoSession) = store.crashed()
        }
        session.progressDelegate = object : GeckoSession.ProgressDelegate {
            override fun onPageStart(session: GeckoSession, url: String) = store.pageStarted(url)
            override fun onPageStop(session: GeckoSession, success: Boolean) = store.pageStopped()
            override fun onProgressChange(session: GeckoSession, progress: Int) = store.progressChanged(progress)
        }
        session.navigationDelegate = object : GeckoSession.NavigationDelegate {
            override fun onLocationChange(
                session: GeckoSession,
                url: String?,
                perms: List<GeckoSession.PermissionDelegate.ContentPermission>,
                hasUserGesture: Boolean,
            ) = store.locationChanged(url)

            override fun onCanGoBack(session: GeckoSession, canGoBack: Boolean) = store.canGoBackChanged(canGoBack)

            override fun onCanGoForward(session: GeckoSession, canGoForward: Boolean) = store.canGoForwardChanged(canGoForward)
        }
        session.open(runtime)
        session.loadUri("about:blank")
    }

    /** Loads web pages and the allowed `about:` pages; anything else (`javascript:`, `file:`, `about:config`) is refused. */
    fun load(url: String) {
        if (isLoadableUrl(url)) session.loadUri(url)
    }

    /** After a crash this re-opens the session and loads the last URL; the history is lost. */
    fun reload() {
        if (!state.value.isCrashed) return session.reload()
        val lastUrl = state.value.url
        store.recovering()
        session.open(runtime)
        view?.let {
            it.releaseSession()
            it.setSession(session)
        }
        load(lastUrl)
    }

    fun stop() = session.stop()

    fun goBack() = session.goBack()

    fun goForward() = session.goForward()

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
        it.setSession(session)
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
