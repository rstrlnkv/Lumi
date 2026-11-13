package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.address.SiteStatus
import ru.digitalpunk.lumi.data.AddressBarStyle
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.PageState

/** A full-width strip down to the screen edge: address pill on top, Back, Forward and More below. */
@Composable
fun ClassicBar(
    state: PageState,
    site: SiteStatus,
    onShowProtection: () -> Unit,
    engine: BrowserEngine,
    tabs: TabActions,
    library: LibraryActions,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    editor: AddressEditor,
    onSwitchStyle: () -> Unit,
    onOpenSettings: () -> Unit,
    progress: () -> Float,
    collapsed: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // One colour in every state, so the suggestion sheet meets the bar without a seam.
    val container = MaterialTheme.colorScheme.surfaceContainer
    Box(modifier.fillMaxWidth().background(container)) {
        Column(
            Modifier
                .graphicsLayer { alpha = controlsAlpha(progress()) }
                .inertWhen(collapsed)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AddressField(
                state = state,
                site = site,
                style = AddressBarStyle.Classic,
                editing = editing,
                onEditingChange = onEditingChange,
                editor = editor,
                onSubmit = engine::load,
                onReload = engine::reload,
                onStop = engine::stop,
                onShowProtection = onShowProtection,
                tabs = tabs,
            )
            if (!editing) {
                var quickOpen by remember { mutableStateOf(false) }
                Box(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        BackButton(state.canGoBack, engine::goBack, state.history, engine::gotoHistoryIndex)
                        ForwardButton(state.canGoForward, engine::goForward)
                        TabsButton(tabs.count, tabs.openGrid, onLongClick = { quickOpen = true })
                        MoreMenu(AddressBarStyle.Classic, tabs, library, onSwitchStyle, onOpenSettings)
                    }
                    // The quick actions hang from the right edge, as the Compact bar's do: a menu anchored on the
                    // mid-row Tabs button would start there instead.
                    Box(Modifier.align(Alignment.CenterEnd).size(48.dp)) {
                        QuickActionsMenu(quickOpen, { quickOpen = false }, tabs)
                    }
                }
            }
        }
        CollapsedStrip(
            state.url,
            site,
            tappable = collapsed,
            onExpand = onExpand,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = stripAlpha(progress()) },
        )
    }
}
