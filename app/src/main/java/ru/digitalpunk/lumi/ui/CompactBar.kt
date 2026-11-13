package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.address.SiteStatus
import ru.digitalpunk.lumi.data.AddressBarStyle
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.PageState

private val BarSidePadding = 20.dp

/** Back (with Forward once the tab can go forward), address pill and More as separate floating controls over the page; no bar background. */
@Composable
fun CompactBar(
    state: PageState,
    site: SiteStatus,
    onShowProtection: () -> Unit,
    engine: BrowserEngine,
    tabs: TabActions,
    library: LibraryActions,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSwitchStyle: () -> Unit,
    onOpenSettings: () -> Unit,
    progress: () -> Float,
    collapsed: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .graphicsLayer { alpha = controlsAlpha(progress()) }
                .inertWhen(collapsed)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .navigationBarsPadding()
                .padding(horizontal = BarSidePadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val nav = navPillState(editing, state.canGoForward)
            if (nav != NavPillState.Hidden) {
                NavPill(state.canGoBack, nav == NavPillState.BackAndForward, engine::goBack, state.history, engine::gotoHistoryIndex, engine::goForward)
            }
            AddressField(
                state = state,
                site = site,
                style = AddressBarStyle.Compact,
                editing = editing,
                onEditingChange = onEditingChange,
                onSubmit = engine::load,
                onReload = engine::reload,
                onStop = engine::stop,
                onShowProtection = onShowProtection,
                modifier = Modifier.weight(1f),
                tabs = tabs,
            )
            if (!editing) FloatingCircle { MoreMenu(AddressBarStyle.Compact, tabs, library, onSwitchStyle, onOpenSettings) }
        }
        CollapsedStrip(
            state.url,
            site,
            tappable = collapsed,
            onExpand = onExpand,
            modifier = Modifier.align(Alignment.TopCenter).graphicsLayer { alpha = stripAlpha(progress()) },
            visual = Modifier
                .shadow(1.dp, CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                .padding(horizontal = 14.dp),
        )
    }
}
