package ru.digitalpunk.lumi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.engine.BrowserEngine

@Composable
fun BrowserScreen(engine: BrowserEngine) {
    val state by engine.state.collectAsStateWithLifecycle()
    BackHandler(enabled = state.canGoBack) { engine.goBack() }

    Column(Modifier.fillMaxSize().imePadding()) {
        Box(Modifier.weight(1f).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top))) {
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
        }
        AddressBar(
            state = state,
            onSubmit = engine::load,
            onReload = engine::reload,
            onStop = engine::stop,
            modifier = Modifier.navigationBarsPadding(),
        )
    }
}
