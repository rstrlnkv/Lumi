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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.data.AddressBarStyle
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.PageState

/** A full-width strip down to the screen edge: address pill on top, Back, Forward and More below. */
@Composable
fun ClassicBar(
    state: PageState,
    engine: BrowserEngine,
    tabs: TabActions,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSwitchStyle: () -> Unit,
    progress: () -> Float,
    collapsed: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = if (editing) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceContainer
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
                style = AddressBarStyle.Classic,
                editing = editing,
                onEditingChange = onEditingChange,
                onSubmit = engine::load,
                onReload = engine::reload,
                onStop = engine::stop,
                tabs = tabs,
            )
            if (!editing) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    BackButton(state.canGoBack, engine::goBack)
                    ForwardButton(state.canGoForward, engine::goForward)
                    MoreMenu(AddressBarStyle.Classic, tabs, onSwitchStyle)
                }
            }
        }
        CollapsedStrip(
            state.url,
            tappable = collapsed,
            onExpand = onExpand,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .graphicsLayer { alpha = stripAlpha(progress()) },
        )
    }
}
