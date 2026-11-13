package ru.digitalpunk.lumi.ui

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
import androidx.compose.material3.SnackbarDuration
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
import ru.digitalpunk.lumi.engine.BrowserEngine
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
import kotlin.math.roundToInt

private val TouchSlop = 8.dp
private const val MIN_REFRESH_MILLIS = 600L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(engine: BrowserEngine, settings: SettingsStore, snapshots: TabSnapshots, gate: PrivateGate) {
    val state by engine.state.collectAsStateWithLifecycle()
    val tabsState by engine.tabs.collectAsStateWithLifecycle()
    val style by settings.addressBarStyle.collectAsStateWithLifecycle<AddressBarStyle?>(null)
    val offHosts by settings.protectionOffHosts.collectAsStateWithLifecycle(emptySet())
    val switchToNewTabs by settings.switchToNewTabs.collectAsStateWithLifecycle(false)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    var editing by remember { mutableStateOf(false) }
    var gridOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    BackHandler(enabled = state.canGoBack && !editing) { engine.goBack() }
    // After the page-history handler, so it comes first: Back closes the grid or Settings before anything else.
    BackHandler(enabled = gridOpen) { gridOpen = false }
    BackHandler(enabled = settingsOpen) { settingsOpen = false }
    // Settings covers the page as the grid does: no snapshots, and the page is out of TalkBack.
    LaunchedEffect(gridOpen || settingsOpen) { engine.gridOpen = gridOpen || settingsOpen }

    // Private tabs: the bar turns dark, recents and screenshots go blank, and a private tab that was in front when
    // Lumi locked waits behind the lock screen. Back there gives it up and opens the grid.
    val selectedPrivate = tabsState.tabs.any { it.id == tabsState.selectedId && it.isPrivate }
    val lockShown = tabsState.privateResume && !gridOpen
    SecureWindow(needsSecureWindow(tabsState, gridOpen), darkBar = selectedPrivate && !gridOpen && !lockShown && !settingsOpen)
    BackHandler(enabled = lockShown) {
        engine.leavePrivate()
        gridOpen = true
    }
    LaunchedEffect(lockShown) {
        if (lockShown) {
            editing = false
            settingsOpen = false
        }
    }
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
            editing = true
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
            editing = true
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
                if (closeEffect(engine.closeTab(it)) == CloseEffect.LeaveGridForNewTab) editing = true
            }
        },
    )

    // Private tabs that could not come back are told once, above the bar.
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
    LaunchedEffect(notice) {
        val shown = notice ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(if (shown.private) openedPrivateText else openedText, openText, duration = SnackbarDuration.Short)
        if (result == SnackbarResult.ActionPerformed) {
            val open = {
                engine.selectTab(shown.tab)
                gridOpen = false
            }
            // A locked private tab cannot be selected: unlock first.
            if (shown.private) gate.enter(open, noScreenLock) else open()
        }
        notice = notice.clearedBy(shown)
    }

    // The link sheet belongs to the tab it was opened on; plain remember keeps a private URL out of the saved state.
    var link by remember { mutableStateOf<LinkTarget?>(null) }
    LaunchedEffect(engine) { engine.linkMenu.collect { link = it } }
    val shownLink = link?.takeIf { it.tab == tabsState.selectedId && !gridOpen && !lockShown && !settingsOpen }
    LaunchedEffect(shownLink == null) { if (shownLink == null) link = null }
    val copiedText = stringResource(R.string.snackbar_link_copied)
    val onLinkAction = { target: LinkTarget, action: LinkAction ->
        link = null
        when (action) {
            LinkAction.Open -> engine.load(target.url)
            LinkAction.Copy -> {
                copyLink(context, target)
                // Android 13 and later show their own confirmation.
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) scope.launch { snackbar.showSnackbar(copiedText) }
            }
            LinkAction.Share -> context.startActivity(
                Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, target.url), null),
            )
            else -> linkOpen(action, switchToNewTabs)?.let { plan ->
                val open = {
                    val id = engine.newTab(private = plan.private, url = target.url, select = plan.select)
                    if (plan.showNotice) notice = NewTabNotice(id, plan.private)
                    if (plan.select) gridOpen = false
                }
                if (plan.needsGate) gate.enter(open, noScreenLock) else open()
            }
        }
    }

    // The page fills the screen under the bar; only the keyboard shrinks it.
    // While the grid covers them, the page and the bar are out of TalkBack and keyboard focus.
    val hiddenBehindGrid = if (gridOpen || lockShown || settingsOpen) Modifier.clearAndSetSemantics {}.focusProperties { canFocus = false } else Modifier
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
                        state, site, openProtection, engine, tabActions, editing, { editing = it }, switchStyle, openSettings, progress, fullyCollapsed, bar::expand, barModifier,
                    )
                    AddressBarStyle.Classic -> ClassicBar(
                        state, site, openProtection, engine, tabActions, editing, { editing = it }, switchStyle, openSettings, progress, fullyCollapsed, bar::expand, barModifier,
                    )
                }
            }
        }

        if (settingsOpen) {
            SettingsScreen(
                switchToNewTabs,
                { scope.launch { settings.setSwitchToNewTabs(it) } },
                onBack = { settingsOpen = false },
                Modifier.fillMaxSize().pointerInput(Unit) {},
            )
        }

        if (lockShown) PrivateLockScreen(onUnlock = { gate.requestUnlock() })

        shownLink?.let { target -> LinkSheet(target, { onLinkAction(target, it) }, onDismiss = { link = null }) }

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
                        editing = true
                    }
                },
                onNewTab = {
                    if (tabsState.currentGroup == PRIVATE_GROUP) {
                        newPrivateTab()
                    } else {
                        engine.newTab()
                        gridOpen = false
                        editing = true
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
                        editing = true
                    }
                    replaced
                },
                onMoveTab = engine::moveTab,
                onUnlock = { gate.requestUnlock() },
            )
        }

        val openNewTabLabel = stringResource(R.string.cd_snackbar_open_new_tab)
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = with(density) { expandedPx.toDp() })) { data ->
            val action = data.visuals.actionLabel
            if (action == null) {
                Snackbar(data)
            } else {
                Snackbar(
                    modifier = Modifier.padding(12.dp),
                    action = {
                        TextButton(
                            onClick = data::performAction,
                            colors = ButtonDefaults.textButtonColors(contentColor = SnackbarDefaults.actionColor),
                            modifier = Modifier.semantics { contentDescription = openNewTabLabel },
                        ) { Text(action) }
                    },
                ) { Text(data.visuals.message) }
            }
        }
    }
}

private const val GRID_FADE_MILLIS = 200

/** Puts the link on the clipboard; from a private tab it is marked sensitive so Android hides its preview. */
private fun copyLink(context: Context, target: LinkTarget) {
    val clip = ClipData.newPlainText("link", target.url)
    if (target.inPrivateTab && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply { putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true) }
    }
    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}
