package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.engine.BackHistory
import ru.digitalpunk.lumi.engine.visibleHistoryRows
import ru.digitalpunk.lumi.privacy.LocalAppColorScheme
import ru.digitalpunk.lumi.ui.tabgrid.cardTitle

/** A 48 dp round bar button, like `IconButton`, that also takes a long press; a null [onLongClick] gives it none. */
@Composable
fun LongPressIconButton(
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    longClickLabel: String?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    val color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Box(
        modifier.minimumInteractiveComponentSize().size(48.dp).clip(CircleShape).combinedClickable(
            enabled = enabled,
            role = Role.Button,
            onLongClickLabel = longClickLabel,
            onLongClick = onLongClick,
            onClick = onClick,
        ),
        contentAlignment = Alignment.Center,
    ) { CompositionLocalProvider(LocalContentColor provides color, content = content) }
}

/** A bar menu in the app's own colours, also under the private tab's dark bar. */
@Composable
fun LumiDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    scrollState: ScrollState = rememberScrollState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    MaterialTheme(colorScheme = LocalAppColorScheme.current ?: MaterialTheme.colorScheme) {
        DropdownMenu(expanded, onDismiss, modifier, scrollState = scrollState, content = content)
    }
}

/** The tab's back history above the Back button: oldest at the top, the current page at the bottom, marked and in view. */
@Composable
fun TabHistoryMenu(history: BackHistory, expanded: Boolean, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    val scroll = rememberScrollState()
    // Menus start at the top; the nearest entries are at the bottom.
    LaunchedEffect(expanded) {
        if (expanded) scroll.scrollTo(snapshotFlow { scroll.maxValue }.first { it > 0 })
    }
    val untitled = stringResource(R.string.tab_untitled)
    LumiDropdownMenu(
        expanded,
        onDismiss,
        Modifier.heightIn(max = 48.dp * visibleHistoryRows(history.rows.size) + 16.dp),
        scroll,
    ) {
        history.rows.forEach { row ->
            val label = cardTitle(row.title, row.url, untitled)
            if (row.isCurrent) {
                val current = stringResource(R.string.cd_history_current_item, label)
                val onColor = MaterialTheme.colorScheme.onSecondaryContainer
                DropdownMenuItem(
                    text = { Text(label, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = onDismiss,
                    leadingIcon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    colors = MenuDefaults.itemColors(textColor = onColor, leadingIconColor = onColor),
                    modifier = Modifier.background(MaterialTheme.colorScheme.secondaryContainer).semantics {
                        contentDescription = current
                        selected = true
                    },
                )
            } else {
                DropdownMenuItem(
                    text = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        onDismiss()
                        onPick(row.index)
                    },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_history), contentDescription = null) },
                )
            }
        }
    }
}

/** New tab, new private tab and close tab: the long press on Tabs (Classic) and More (Compact). */
@Composable
fun QuickActionsMenu(expanded: Boolean, onDismiss: () -> Unit, tabs: TabActions) {
    LumiDropdownMenu(expanded, onDismiss) {
        MenuItem(stringResource(R.string.menu_new_tab), { Icon(Icons.Filled.Add, null) }) { onDismiss(); tabs.newTab() }
        MenuItem(stringResource(R.string.menu_new_private_tab), { Icon(painterResource(R.drawable.ic_domino_mask), null) }) {
            onDismiss(); tabs.newPrivateTab()
        }
        MenuItem(stringResource(R.string.menu_close_tab), { Icon(Icons.Filled.Close, null) }) { onDismiss(); tabs.closeTab() }
    }
}

/** A menu row with a leading icon; the icon is decoration, the text is what TalkBack reads. */
@Composable
fun MenuItem(text: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(text) }, onClick = onClick, leadingIcon = icon)
}
