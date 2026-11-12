package ru.digitalpunk.lumi.ui.tabgrid

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.engine.TabInfo
import ru.digitalpunk.lumi.tabs.TabSnapshots

private const val MIN_ALPHA = 0.4f

/** One tab in the grid: swipe it sideways (or use the close button) to close it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabCard(
    tab: TabInfo,
    position: Int,
    total: Int,
    selected: Boolean,
    snapshots: TabSnapshots,
    onSelect: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = cardTitle(tab.title, tab.url, stringResource(R.string.tab_untitled))
    val dismiss = rememberSwipeToDismissBoxState(positionalThreshold = { it * DISMISS_FRACTION })
    // A card that has been swept away is closed once; the grid then drops it.
    LaunchedEffect(dismiss.currentValue) {
        if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) onClose()
    }
    val description = stringResource(R.string.cd_tab_card, title, position, total)
    val current = stringResource(R.string.state_current_tab)
    val closeAction = stringResource(R.string.action_close_tab_custom)
    SwipeToDismissBox(
        state = dismiss,
        backgroundContent = {},
        modifier = modifier,
    ) {
        val shape = MaterialTheme.shapes.large
        Box(
            Modifier
                .graphicsLayer {
                    val offset = runCatching { dismiss.requireOffset() }.getOrDefault(0f)
                    alpha = swipeAlpha(offset, size.width, MIN_ALPHA)
                }
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .clickable(onClick = onSelect)
                .semantics(mergeDescendants = true) {
                    contentDescription = description
                    if (selected) stateDescription = current
                    customActions = listOf(CustomAccessibilityAction(closeAction) { onClose(); true })
                },
        ) {
            CardContent(title, tab, snapshots, onClose)
            if (selected) {
                Box(Modifier.matchParentSize().border(3.dp, MaterialTheme.colorScheme.primary, shape))
            }
        }
    }
}

@Composable
private fun CardContent(title: String, tab: TabInfo, snapshots: TabSnapshots, onClose: () -> Unit) {
    Column {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(start = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.size(16.dp).background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    cardInitial(title),
                    style = MaterialTheme.typography.labelSmall,
                    // Fixed size: the 16 dp circle must not clip at large font scales.
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onPrimary,
                    maxLines = 1,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
            Text(
                title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            // The design draws a 32 dp button; the 48 dp header makes it a true 48 x 48 dp touch target.
            IconButton(
                onClick = onClose,
                colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                modifier = Modifier.size(48.dp),
            ) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close_tab, title), modifier = Modifier.size(18.dp))
            }
        }
        val image: ImageBitmap? by produceState(snapshots.cached(tab.id), tab.id, snapshots.versionOf(tab.id)) {
            value = snapshots.load(tab.id)
        }
        Box(Modifier.fillMaxWidth().aspectRatio(SNAPSHOT_ASPECT).background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
            image?.let {
                Image(
                    it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.TopCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** The snapshot area is 182 x 220 dp at the design width. */
private const val SNAPSHOT_ASPECT = 182f / 220f
