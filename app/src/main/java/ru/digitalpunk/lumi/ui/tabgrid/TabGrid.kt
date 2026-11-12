package ru.digitalpunk.lumi.ui.tabgrid

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.engine.TabId
import ru.digitalpunk.lumi.engine.TabInfo
import ru.digitalpunk.lumi.tabs.TabSnapshots

/** All tabs as cards, two to a row on a phone in portrait (more on wider screens), over the page. A tap on a card selects it; the toolbar has New tab, the count and Done. */
@Composable
fun TabGrid(
    tabs: List<TabInfo>,
    selectedId: TabId?,
    snapshots: TabSnapshots,
    onSelect: (TabId) -> Unit,
    onClose: (TabId) -> Unit,
    onNewTab: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        val state = rememberLazyGridState(initialFirstVisibleItemIndex = tabs.indexOfFirst { it.id == selectedId }.coerceAtLeast(0))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            state = state,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .statusBarsPadding()
                .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        ) {
            itemsIndexed(tabs, key = { _, tab -> tab.id.value }) { index, tab ->
                TabCard(
                    tab = tab,
                    position = index + 1,
                    total = tabs.size,
                    selected = tab.id == selectedId,
                    snapshots = snapshots,
                    onSelect = { onSelect(tab.id) },
                    onClose = { onClose(tab.id) },
                    modifier = Modifier.animateItem(),
                )
            }
        }
        // The toolbar's colour runs down through the navigation bar.
        Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).navigationBarsPadding()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .padding(start = 4.dp, end = 8.dp),
            ) {
                IconButton(
                    onClick = onNewTab,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(48.dp).align(Alignment.CenterStart),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_new_tab))
                }
                Text(
                    pluralStringResource(R.plurals.tab_count, tabs.size, tabs.size),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
                TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text(stringResource(R.string.action_done), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
