package ru.digitalpunk.lumi.ui

import android.provider.Settings
import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalResources
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.MutableStateFlow
import ru.digitalpunk.lumi.data.LibraryTab
import ru.digitalpunk.lumi.downloads.DownloadAsks
import ru.digitalpunk.lumi.downloads.DownloadEvent
import ru.digitalpunk.lumi.downloads.DownloadManager
import ru.digitalpunk.lumi.downloads.showsDownloadSnackbar
import ru.digitalpunk.lumi.ui.library.openDownload
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.address.SiteStatus
import ru.digitalpunk.lumi.address.displayUrl
import ru.digitalpunk.lumi.address.siteStatus
import ru.digitalpunk.lumi.data.AddressBarStyle
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.data.search.EngineSuggestClient
import ru.digitalpunk.lumi.data.search.SearchEngine
import ru.digitalpunk.lumi.suggest.LocalIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.ContextTarget
import ru.digitalpunk.lumi.engine.LinkTarget
import ru.digitalpunk.lumi.engine.PRIVATE_GROUP
import ru.digitalpunk.lumi.engine.PageTouchInfo
import ru.digitalpunk.lumi.engine.isPrivate
import ru.digitalpunk.lumi.privacy.LocalAppColorScheme
import ru.digitalpunk.lumi.privacy.LocalPrivateTab
import ru.digitalpunk.lumi.privacy.PrivateGate
import ru.digitalpunk.lumi.privacy.PrivateLockScreen
import ru.digitalpunk.lumi.privacy.SecureWindow
import ru.digitalpunk.lumi.privacy.barScheme
import ru.digitalpunk.lumi.privacy.needsSecureWindow
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import ru.digitalpunk.lumi.engine.PageTouchListener
import ru.digitalpunk.lumi.engine.TabInfo
import ru.digitalpunk.lumi.engine.neighbour
import ru.digitalpunk.lumi.engine.siteKey
import ru.digitalpunk.lumi.gesture.BarGeometry
import ru.digitalpunk.lumi.gesture.PageGestureTracker
import ru.digitalpunk.lumi.gesture.pullFraction
import ru.digitalpunk.lumi.tabs.TabSnapshots
import ru.digitalpunk.lumi.ui.tabgrid.CloseEffect
import ru.digitalpunk.lumi.ui.tabgrid.TabGrid
import ru.digitalpunk.lumi.ui.tabgrid.closeEffect
import ru.digitalpunk.lumi.ui.tabgrid.currentGroupTabCount
import ru.digitalpunk.lumi.ui.settings.SettingsScreen
import ru.digitalpunk.lumi.ui.library.BookmarkEditorDialog
import ru.digitalpunk.lumi.ui.library.LibraryScreen
import ru.digitalpunk.lumi.ui.library.deleteWithUndo
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryRepository
import ru.digitalpunk.lumi.data.library.children
import ru.digitalpunk.lumi.data.library.effectiveStartFolder
import ru.digitalpunk.lumi.data.library.findBookmarkByUrl
import ru.digitalpunk.lumi.startpage.backGoesUp
import ru.digitalpunk.lumi.startpage.focusNewTab
import ru.digitalpunk.lumi.startpage.navInto
import ru.digitalpunk.lumi.startpage.navUp
import ru.digitalpunk.lumi.startpage.reconcile
import ru.digitalpunk.lumi.startpage.startPageVisible
import ru.digitalpunk.lumi.ui.startpage.StartPage
import ru.digitalpunk.lumi.library.canBookmark
import ru.digitalpunk.lumi.library.folderName
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.text.input.clearText
import kotlin.math.roundToInt

private val TouchSlop = 8.dp
private const val MIN_REFRESH_MILLIS = 600L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    engine: BrowserEngine,
    settings: SettingsStore,
    snapshots: TabSnapshots,
    gate: PrivateGate,
    library: LibraryRepository,
    suggestClient: EngineSuggestClient,
    downloads: DownloadManager,
    downloadAsks: DownloadAsks,
    showDownloads: MutableStateFlow<Boolean>,
) {
    val state by engine.state.collectAsStateWithLifecycle()
    val tabsState by engine.tabs.collectAsStateWithLifecycle()
    val style by settings.addressBarStyle.collectAsStateWithLifecycle<AddressBarStyle?>(null)
    val offHosts by settings.protectionOffHosts.collectAsStateWithLifecycle(emptySet())
    val switchToNewTabs by settings.switchToNewTabs.collectAsStateWithLifecycle(false)
    // Null until read: no suggestion request goes out before the stored switch is known.
    val searchEngine by settings.searchEngine.collectAsStateWithLifecycle<SearchEngine?>(null)
    val searchSuggestions by settings.searchSuggestions.collectAsStateWithLifecycle<Boolean?>(null)
    // The start page shows the start folder; a new tab focuses the address field only when that folder is empty.
    val nodes by library.nodes.collectAsStateWithLifecycle(emptyList())
    val startStored by settings.startPageFolder.collectAsStateWithLifecycle(FAVORITES)
    val startFolder = effectiveStartFolder(nodes, startStored)
    val focusNew = focusNewTab(children(nodes, startFolder).size)
    val icons = LocalSiteIcons.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var editing by remember { mutableStateOf(false) }
    val editor = remember { AddressEditor() }
    LaunchedEffect(searchEngine) { editor.searchPrefix = (searchEngine ?: SearchEngine.Google).searchPrefix }
    // Loaded once per edit; nothing typed is stored, in any tab, nor kept once the bar closes.
    LaunchedEffect(editing) {
        if (!editing) editor.text.clearText()
        editor.index = if (editing) library.suggestionPages().let { withContext(Dispatchers.Default) { LocalIndex(it) } } else null
    }
    var gridOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var libraryOpen by rememberSaveable { mutableStateOf(false) }
    // Only the id: a bookmark is in the normal store, so nothing private is kept in the saved state.
    var editBookmarkId by rememberSaveable { mutableStateOf<String?>(null) }
    var capturing by remember { mutableStateOf(false) }
    BackHandler(enabled = state.canGoBack && !editing) { engine.goBack() }
    // After the page-history handler, so it comes first: Back closes the grid or Settings before anything else.
    BackHandler(enabled = gridOpen) { gridOpen = false }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    // The Library installs its own Back handler (search, folder levels, then close).
    // Settings and the Library cover the page as the grid does: no snapshots, and the page is out of TalkBack.
    val coverOpen = gridOpen || settingsOpen || libraryOpen
    LaunchedEffect(coverOpen) { engine.gridOpen = coverOpen }

    // Private tabs: the bar turns dark, recents and screenshots go blank, and a private tab that was in front when
    // Lumi locked waits behind the lock screen. Back there gives it up and opens the grid.
    val selectedPrivate = tabsState.tabs.any { it.id == tabsState.selectedId && it.isPrivate }
    val lockShown = tabsState.privateResume && !gridOpen
    SecureWindow(needsSecureWindow(tabsState, gridOpen), darkBar = selectedPrivate && !coverOpen && !lockShown)
    BackHandler(enabled = lockShown) {
        engine.leavePrivate()
        gridOpen = true
    }
    LaunchedEffect(lockShown) {
        if (lockShown) {
            editing = false
            settingsOpen = false
            libraryOpen = false
            editBookmarkId = null
        }
    }
    // The start page's open folder, per tab: ids only, so nothing private is saved. Hiding the start page resets it.
    val startShown = startPageVisible(state.url, state.isLoading, state.isCrashed)
    var startStack by rememberSaveable(tabsState.selectedId?.value) { mutableStateOf(listOf<String>()) }
    LaunchedEffect(startShown, nodes, startFolder) { startStack = if (startShown) reconcile(startStack, nodes, startFolder) else emptyList() }
    // Registered after the grid's and Settings' handlers, so `covered` keeps them first.
    BackHandler(enabled = backGoesUp(startShown, startStack, editing, coverOpen || lockShown)) { startStack = navUp(startStack) }

    // One prompt per lock, on return to a private tab.
    LaunchedEffect(tabsState.privateResume, gate.autoPromptPending) {
        if (tabsState.privateResume && gate.autoPromptPending) {
            gate.autoPromptPending = false
            gate.requestUnlock()
        }
    }
    val context = LocalContext.current
    val noScreenLock = { Toast.makeText(context, R.string.private_needs_screen_lock, Toast.LENGTH_LONG).show() }
    val newPrivateTab = {
        gate.enter({
            engine.newTab(private = true)
            gridOpen = false
            editing = focusNew
        }, noScreenLock)
    }

    // The protection sheet is about the page on screen: it is kept for the tab it was opened on and goes when
    // another tab is selected, the grid opens or the page stops being a web site.
    // A crashed page shows no shield: nothing on screen is protected.
    val site = if (state.isCrashed) SiteStatus.None else siteStatus(state.url, state.blockedTrackers, offHosts, selectedPrivate)
    val siteHost = siteKey(state.url)
    var protectionTab by rememberSaveable { mutableStateOf<String?>(null) }
    val showProtection = protectionTab != null && protectionTab == tabsState.selectedId?.value && !gridOpen && siteHost != null
    LaunchedEffect(showProtection) { if (!showProtection) protectionTab = null }
    val openProtection = { protectionTab = tabsState.selectedId?.value }

    // The bar slides off by `bar.hidden`; Gecko is told how much of the page bottom the bar covers.
    val bar = remember(style) { BarController(scope) }
    var expandedPx by remember(style) { mutableIntStateOf(0) }
    val navInsetPx = WindowInsets.navigationBars.getBottom(density)
    val collapsedPx = with(density) {
        CollapsedStripHeight.toPx()
    } + navInsetPx
    LaunchedEffect(bar, expandedPx, collapsedPx) {
        if (expandedPx == 0) return@LaunchedEffect
        bar.setMax(BarGeometry.maxHidden(expandedPx.toFloat(), collapsedPx))
        engine.setBarMaxHeight(expandedPx)
        snapshotFlow { BarGeometry.clipping(expandedPx.toFloat(), bar.hidden).roundToInt() }
            .distinctUntilChanged()
            .collect(engine::setBarVisibleHeight)
    }
    val fullyCollapsed by remember(bar) { derivedStateOf { bar.isCollapsed } }
    // A bar that is slid away comes back when typing starts, the page crashes or another page loads.
    LaunchedEffect(editing, state.isCrashed, state.url) { bar.expand() }

    // Pull to refresh: the engine reports the finger, the tracker decides, nothing here scrolls.
    val pullState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(state)
    LaunchedEffect(refreshing) {
        if (!refreshing) return@LaunchedEffect
        delay(MIN_REFRESH_MILLIS)
        snapshotFlow { current }.first { !it.isLoading || it.isCrashed }
        refreshing = false
        pullState.animateToHidden()
    }
    val thresholdPx = with(density) { PullToRefreshDefaults.PositionalThreshold.toPx() }
    val tracker = remember(bar, thresholdPx) {
        PageGestureTracker(
            slopPx = with(density) { TouchSlop.toPx() },
            pullThresholdPx = thresholdPx,
            isBarExpanded = { bar.isExpanded },
            sink = object : PageGestureTracker.Sink {
                override fun barDrag(dy: Float) = bar.drag(dy)
                override fun barRelease() = bar.settle()
                override fun pull(distancePx: Float) {
                    scope.launch { pullState.snapTo(pullFraction(distancePx, thresholdPx)) }
                }

                override fun pullRelease(refresh: Boolean) {
                    scope.launch {
                        if (refresh) {
                            refreshing = true
                            engine.reload()
                            pullState.animateToThreshold()
                        } else {
                            pullState.animateToHidden()
                        }
                    }
                }
            },
        )
    }
    DisposableEffect(tracker) {
        engine.pageTouchListener = object : PageTouchListener {
            override fun onTouchDown() = tracker.down()
            override fun onTouchDownResolved(info: PageTouchInfo) = tracker.resolved(info)
            override fun onTouchMove(dyPx: Float) = tracker.move(dyPx)
            override fun onTouchEnd(cancelled: Boolean) = tracker.end(cancelled)
        }
        onDispose { engine.pageTouchListener = null }
    }

    // Nothing to show until the stored tabs are back.
    if (tabsState.tabs.isEmpty()) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface))
        return
    }

    val untitled = stringResource(R.string.tab_untitled)
    fun hostOf(tab: TabInfo?) = tab?.let { displayUrl(it.url).text.ifEmpty { untitled } }
    val tabActions = TabActions(
        selectedId = tabsState.selectedId,
        count = currentGroupTabCount(tabsState),
        prevHost = hostOf(tabsState.neighbour(-1)),
        nextHost = hostOf(tabsState.neighbour(1)),
        newTab = {
            engine.newTab()
            editing = focusNew
        },
        newPrivateTab = newPrivateTab,
        openGrid = {
            // A second request while the picture is being taken (or the grid is open) changes nothing.
            if (!capturing && !gridOpen) {
                capturing = true
                scope.launch {
                    try {
                        engine.captureSelected()
                        gridOpen = true
                    } finally {
                        capturing = false
                    }
                }
            }
        },
        switchBy = { engine.switchBy(it) },
        closeTab = {
            tabsState.selectedId?.let {
                if (closeEffect(engine.closeTab(it)) == CloseEffect.LeaveGridForNewTab) editing = focusNew
            }
        },
    )

    // Private tabs that could not come back are told once, above the bar (or above the Library).
    val snackbar = remember { SnackbarHostState() }
    val droppedText = stringResource(R.string.private_tabs_dropped)
    LaunchedEffect(gate) {
        snapshotFlow { gate.dropNoticePending }.first { it }
        gate.dropNoticePending = false
        snackbar.showSnackbar(droppedText)
    }

    // A link opened in the background is told above the bar; a newer one replaces the snackbar, and Open goes to the newest tab.
    var notice by remember { mutableStateOf<NewTabNotice?>(null) }
    val openedText = stringResource(R.string.snackbar_new_tab_opened)
    val openedPrivateText = stringResource(R.string.snackbar_new_private_tab_opened)
    val openText = stringResource(R.string.snackbar_action_open)
    val openNewTabLabel = stringResource(R.string.cd_snackbar_open_new_tab)
    LaunchedEffect(notice) {
        val shown = notice ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(LumiSnackbarVisuals(if (shown.private) openedPrivateText else openedText, openText, openNewTabLabel))
        if (result == SnackbarResult.ActionPerformed) {
            val open = {
                engine.selectTab(shown.tab)
                gridOpen = false
                libraryOpen = false
            }
            // A locked private tab cannot be selected: unlock first.
            if (shown.private) gate.enter(open, noScreenLock) else open()
        }
        notice = notice.clearedBy(shown)
    }

    // The link and image sheet belongs to the tab it was opened on; plain remember keeps a private URL out of the saved state.
    var press by remember { mutableStateOf<ContextTarget?>(null) }
    LaunchedEffect(engine) { engine.linkMenu.collect { press = it } }
    val shownPress = press?.takeIf { it.tab == tabsState.selectedId && !coverOpen && !lockShown }
    LaunchedEffect(shownPress == null) { if (shownPress == null) press = null }

    // Downloads (M7c1): "Show" and a download notification open the Library on the Downloads tab.
    val downloadsRequested by showDownloads.collectAsStateWithLifecycle()
    LaunchedEffect(downloadsRequested) {
        if (!downloadsRequested) return@LaunchedEffect
        showDownloads.value = false
        settings.setLibraryTab(LibraryTab.Downloads)
        editing = false
        libraryOpen = true
    }
    val libraryTab by settings.libraryTab.collectAsStateWithLifecycle(LibraryTab.Bookmarks)
    val downloadsOnScreen by rememberUpdatedState(libraryOpen && libraryTab == LibraryTab.Downloads)
    val downloadContext = LocalContext.current
    val downloadResources = LocalResources.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val downloadLockShown by rememberUpdatedState(lockShown)
    LaunchedEffect(downloads) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            downloads.events.collect { event ->
                if (!showsDownloadSnackbar(event.item, downloadLockShown)) return@collect
                val name = event.item.fileName
                val private = event.item.isPrivate
                // A private file's name stays out of the snackbar, as it does out of the notification.
                fun visuals(text: Int, privateText: Int, action: Int, description: Int, duration: SnackbarDuration) = LumiSnackbarVisuals(
                    if (private) downloadResources.getString(privateText) else downloadResources.getString(text, name),
                    downloadResources.getString(action), downloadResources.getString(description, name), duration,
                )
                when (event) {
                    // The list itself shows a new download.
                    is DownloadEvent.Started -> if (!downloadsOnScreen) launch {
                        val shown = visuals(R.string.download_started, R.string.download_started_private, R.string.action_show, R.string.cd_snackbar_show_downloads, SnackbarDuration.Short)
                        if (snackbar.showSnackbar(shown) == SnackbarResult.ActionPerformed) showDownloads.value = true
                    }
                    is DownloadEvent.Finished -> launch {
                        val shown = visuals(R.string.download_finished, R.string.download_finished_private, R.string.snackbar_action_open, R.string.cd_snackbar_open_file, SnackbarDuration.Long)
                        if (snackbar.showSnackbar(shown) == SnackbarResult.ActionPerformed) openDownload(downloadContext, event.item, false, scope, snackbar)
                    }
                    is DownloadEvent.Failed -> launch {
                        val description = if (private) R.string.action_retry else R.string.cd_download_retry
                        val shown = visuals(R.string.download_failed_snackbar, R.string.download_failed_private, R.string.action_retry, description, SnackbarDuration.Long)
                        if (snackbar.showSnackbar(shown) == SnackbarResult.ActionPerformed) downloads.retry(event.item.id)
                    }
                }
            }
        }
    }
    LaunchedEffect(downloadAsks) {
        downloadAsks.hints.collect {
            launch {
                val hint = LumiSnackbarVisuals(
                    downloadResources.getString(R.string.download_no_notifications), downloadResources.getString(R.string.action_settings), duration = SnackbarDuration.Long,
                )
                if (snackbar.showSnackbar(hint) == SnackbarResult.ActionPerformed) {
                    downloadContext.startActivity(
                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, downloadContext.packageName),
                    )
                }
            }
        }
    }
    val offers by downloadAsks.offers.collectAsStateWithLifecycle()
    val copiedText = stringResource(R.string.snackbar_link_copied)
    val imageCopiedText = stringResource(R.string.snackbar_image_address_copied)
    // The image items reach here with a LinkTarget made from the image's URL.
    val onLinkAction = { target: LinkTarget, action: LinkAction ->
        press = null
        when (action) {
            LinkAction.Open -> engine.load(target.url)
            LinkAction.Copy, LinkAction.CopyImageAddress -> {
                copyLink(context, target)
                // Android 13 and later show their own confirmation.
                val text = if (action == LinkAction.CopyImageAddress) imageCopiedText else copiedText
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) scope.launch { snackbar.showSnackbar(text) }
            }
            LinkAction.Share, LinkAction.ShareImage -> context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, target.url), null),
            )
            else -> linkOpen(action, switchToNewTabs)?.let { plan ->
                val open = {
                    val id = engine.newTab(private = plan.private, url = target.url, select = plan.select)
                    if (plan.showNotice) notice = NewTabNotice(id, plan.private)
                    if (plan.select) {
                        gridOpen = false
                        libraryOpen = false
                    }
                }
                if (plan.needsGate) gate.enter(open, noScreenLock) else open()
            }
        }
    }
    val onContextAction = { target: ContextTarget, action: LinkAction ->
        when (val step = sheetStep(target, action)) {
            is SheetStep.Link -> onLinkAction(step.link, action)
            is SheetStep.Save -> {
                press = null
                downloadAsks.saveImage(step.image)
            }
            null -> Unit
        }
    }

    // Add bookmark saves at once into the start-page folder and fetches the site's icon; a saved page offers Edit instead.
    val savedBookmark = remember(nodes, state.url) { findBookmarkByUrl(nodes, state.url) }
    val favoritesText = stringResource(R.string.folder_favorites)
    val editText = stringResource(R.string.snackbar_action_edit)
    val editBookmarkLabel = stringResource(R.string.cd_snackbar_edit_bookmark)
    val resources = LocalResources.current
    val libraryActions = LibraryActions(
        bookmarked = savedBookmark != null,
        canBookmark = canBookmark(state.url, state.isCrashed),
        addOrEditBookmark = {
            if (savedBookmark != null) {
                editBookmarkId = savedBookmark.id
            } else {
                val url = state.url
                val title = tabsState.tabs.firstOrNull { it.id == tabsState.selectedId }?.title.orEmpty()
                scope.launch {
                    val id = library.addBookmark(startFolder, title, url) ?: return@launch
                    if (!selectedPrivate) icons?.request(url)
                    val folder = nodes.firstOrNull { it.id == startFolder }?.let { folderName(it, favoritesText) } ?: favoritesText
                    val saved = LumiSnackbarVisuals(resources.getString(R.string.snackbar_bookmark_saved, folder), editText, editBookmarkLabel)
                    if (snackbar.showSnackbar(saved) == SnackbarResult.ActionPerformed) editBookmarkId = id
                }
            }
        },
        openLibrary = {
            editing = false
            libraryOpen = true
        },
    )

    // The page fills the screen under the bar; only the keyboard shrinks it.
    // While the grid covers them, the page and the bar are out of TalkBack and keyboard focus.
    val hiddenBehindGrid = if (coverOpen || lockShown) Modifier.clearAndSetSemantics {}.focusProperties { canFocus = false } else Modifier
    Box(Modifier.fillMaxSize().imePadding()) {
        Box(
            Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))
                .then(hiddenBehindGrid),
        ) {
            AndroidView(
                factory = { engine.createView(it) },
                onRelease = { engine.releaseView(it) },
                modifier = Modifier.fillMaxSize(),
            )
            if (startShown) {
                StartPage(
                    library, nodes, startFolder, startStack, selectedPrivate,
                    bottomInset = with(density) { expandedPx.toDp() },
                    snackbar = snackbar,
                    scope = scope,
                    onOpenFolder = { startStack = navInto(startStack, it) },
                    onUp = { startStack = navUp(startStack) },
                    onOpenUrl = engine::load,
                    onOpenInTab = { url, action ->
                        tabsState.selectedId?.let { onLinkAction(LinkTarget(it, url, null, selectedPrivate), action) }
                    },
                    onEdit = { editBookmarkId = it },
                )
            }
            if (state.isCrashed) {
                Column(
                    Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface)
                        .pointerInput(Unit) {}
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(48.dp),
                    )
                    Text(
                        stringResource(R.string.page_crashed_title),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Button(onClick = engine::reload) { Text(stringResource(R.string.page_crashed_reload)) }
                }
            }
            PullToRefreshDefaults.Indicator(state = pullState, isRefreshing = refreshing, modifier = Modifier.align(Alignment.TopCenter))
        }

        if (editing && !coverOpen && !lockShown) {
            SuggestionLayer(
                editor,
                engine = searchEngine.takeIf { searchSuggestions == true },
                client = suggestClient,
                barHeight = with(density) { expandedPx.toDp() },
                onOpen = engine::load,
            )
        }

        val barModifier = Modifier
            .align(Alignment.BottomCenter)
            .onSizeChanged { expandedPx = it.height }
            .graphicsLayer { translationY = bar.hidden }
            .then(hiddenBehindGrid)
        // Nothing to hide (not measured yet, or the keyboard is up): the bar stays fully shown.
        val progress = { if (bar.maxHidden <= 0f) 0f else BarGeometry.progress(bar.hidden, bar.maxHidden) }
        val switchStyle: () -> Unit = {
            scope.launch {
                settings.setAddressBarStyle(
                    if (style == AddressBarStyle.Compact) AddressBarStyle.Classic else AddressBarStyle.Compact,
                )
            }
        }
        // Draw no bar until the stored style is known, so a Classic user never sees Compact first.
        // A private tab's bar uses the fixed dark private scheme.
        val appScheme = MaterialTheme.colorScheme
        val openSettings = { settingsOpen = true }
        MaterialTheme(colorScheme = barScheme(appScheme, selectedPrivate)) {
            CompositionLocalProvider(LocalPrivateTab provides selectedPrivate, LocalAppColorScheme provides appScheme) {
                when (style) {
                    null -> Unit
                    AddressBarStyle.Compact -> CompactBar(
                        state, site, openProtection, engine, tabActions, libraryActions, editing, { editing = it }, editor, switchStyle, openSettings, progress, fullyCollapsed, bar::expand, barModifier,
                    )
                    AddressBarStyle.Classic -> ClassicBar(
                        state, site, openProtection, engine, tabActions, libraryActions, editing, { editing = it }, editor, switchStyle, openSettings, progress, fullyCollapsed, bar::expand, barModifier,
                    )
                }
            }
        }

        if (settingsOpen) {
            SettingsScreen(
                library, nodes, startFolder,
                { scope.launch { settings.setStartPageFolder(it) } },
                switchToNewTabs,
                { scope.launch { settings.setSwitchToNewTabs(it) } },
                searchEngine = searchEngine ?: SearchEngine.Google,
                onSearchEngineChange = { scope.launch { settings.setSearchEngine(it) } },
                searchSuggestions = searchSuggestions ?: true,
                onSearchSuggestionsChange = { scope.launch { settings.setSearchSuggestions(it) } },
                onBack = { settingsOpen = false },
                Modifier.fillMaxSize().pointerInput(Unit) {},
            )
        }

        if (libraryOpen) {
            LibraryScreen(
                library, downloads, settings, snackbar, scope, selectedPrivate, startFolder,
                onOpen = { url ->
                    engine.load(url)
                    libraryOpen = false
                },
                onOpenInTab = { url, action ->
                    tabsState.selectedId?.let { onLinkAction(LinkTarget(it, url, null, selectedPrivate), action) }
                },
                onEdit = { editBookmarkId = it },
                onClose = { libraryOpen = false },
                modifier = Modifier.fillMaxSize().pointerInput(Unit) {},
            )
        }

        editBookmarkId?.let { id ->
            val deleted = stringResource(R.string.snackbar_bookmark_deleted)
            val undo = stringResource(R.string.snackbar_action_undo)
            BookmarkEditorDialog(
                library, nodes, id, selectedPrivate,
                onDelete = {
                    editBookmarkId = null
                    scope.launch { deleteWithUndo(library, id, snackbar, deleted, undo) }
                },
                onDismiss = { editBookmarkId = null },
            )
        }

        if (lockShown) PrivateLockScreen(onUnlock = { gate.requestUnlock() })

        shownPress?.let { target -> LinkSheet(target, { onContextAction(target, it) }, onDismiss = { press = null }) }

        // A private file is never asked about over the private lock screen.
        offers.firstOrNull()?.takeIf { !lockShown }?.let { offer ->
            val name = remember(offer) { downloads.fileName(offer.url, offer.suggestedName, offer.mimeType) }
            DownloadAskSheet(offer, name, onDownload = { downloadAsks.confirm(offer) }, onCancel = { downloadAsks.cancel(offer) })
        }

        if (showProtection) {
            val display = displayUrl(state.url)
            ProtectionSheet(
                host = display.text,
                trackers = state.blockedTrackers,
                off = !selectedPrivate && siteHost in offHosts,
                secure = display.isSecure,
                private = selectedPrivate,
                onToggle = { off ->
                    // The tab and site of the tap: the write can end after the user switched tab or page.
                    tabsState.selectedId?.let { tab ->
                        scope.launch {
                            settings.setProtectionOff(siteHost, off)
                            engine.setProtectionOff(tab, siteHost, off)
                        }
                    }
                },
                onDismiss = { protectionTab = null },
            )
        }

        AnimatedVisibility(
            visible = gridOpen,
            enter = fadeIn(tween(GRID_FADE_MILLIS)) + scaleIn(tween(GRID_FADE_MILLIS), initialScale = 0.96f),
            exit = fadeOut(tween(GRID_FADE_MILLIS)) + scaleOut(tween(GRID_FADE_MILLIS), targetScale = 0.96f),
        ) {
            TabGrid(
                state = tabsState,
                snapshots = snapshots,
                onSelect = {
                    engine.selectTab(it)
                    gridOpen = false
                },
                onClose = {
                    if (closeEffect(engine.closeTab(it)) == CloseEffect.LeaveGridForNewTab) {
                        gridOpen = false
                        editing = focusNew
                    }
                },
                onNewTab = {
                    if (tabsState.currentGroup == PRIVATE_GROUP) {
                        newPrivateTab()
                    } else {
                        engine.newTab()
                        gridOpen = false
                        editing = focusNew
                    }
                },
                onDone = { gridOpen = false },
                onSelectGroup = { group ->
                    engine.setCurrentGroup(group)
                    if (group == PRIVATE_GROUP && engine.tabs.value.privateLocked) gate.requestUnlock()
                },
                onCreateGroup = engine::createGroup,
                onRenameGroup = engine::renameGroup,
                onDeleteGroup = { id ->
                    val replaced = engine.deleteGroup(id)
                    if (closeEffect(replaced) == CloseEffect.LeaveGridForNewTab) {
                        gridOpen = false
                        editing = focusNew
                    }
                    replaced
                },
                onMoveTab = engine::moveTab,
                onUnlock = { gate.requestUnlock() },
            )
        }

        // Over the Library the bar is hidden, so the snackbar sits above the navigation bar instead.
        val snackbarBottom = if (libraryOpen) Modifier.navigationBarsPadding() else Modifier.padding(bottom = with(density) { expandedPx.toDp() })
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).then(snackbarBottom)) { data ->
            val action = data.visuals.actionLabel
            val actionDescription = (data.visuals as? LumiSnackbarVisuals)?.actionDescription
            if (action == null) {
                Snackbar(data)
            } else {
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    action = {
                        TextButton(
                            onClick = data::performAction,
                            colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                            modifier = if (actionDescription == null) Modifier else Modifier.semantics { contentDescription = actionDescription },
                        ) { Text(action) }
                    },
                ) { Text(data.visuals.message) }
            }
        }
    }
}

private const val GRID_FADE_MILLIS = 200

/** Puts the link (or image address) on the clipboard; from a private tab it is marked sensitive so Android hides its preview. */
private fun copyLink(context: Context, target: LinkTarget) {
    val clip = ClipData.newPlainText("link", target.url)
    if (target.inPrivateTab && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}
