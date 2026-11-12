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
import ru.digitalpunk.lumi.data.AddressBarStyle
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.PageState

/** Back, address pill and More as separate floating controls over the page; no bar background. */
@Composable
fun CompactBar(
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
    Box(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .graphicsLayer { alpha = controlsAlpha(progress()) }
                .inertWhen(collapsed)
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!editing) FloatingCircle { BackButton(state.canGoBack, engine::goBack) }
            AddressField(
                state = state,
                style = AddressBarStyle.Compact,
                editing = editing,
                onEditingChange = onEditingChange,
                onSubmit = engine::load,
                onReload = engine::reload,
                onStop = engine::stop,
                modifier = Modifier.weight(1f),
                tabs = tabs,
            )
            if (!editing) FloatingCircle { MoreMenu(AddressBarStyle.Compact, tabs, onSwitchStyle) }
        }
        CollapsedStrip(
            state.url,
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
