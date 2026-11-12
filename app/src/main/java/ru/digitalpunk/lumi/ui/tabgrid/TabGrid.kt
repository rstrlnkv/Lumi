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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.engine.GroupId
import ru.digitalpunk.lumi.engine.PRIVATE_GROUP
import ru.digitalpunk.lumi.engine.isPrivate
import ru.digitalpunk.lumi.privacy.PrivateEmptyColumn
import ru.digitalpunk.lumi.privacy.PrivateLockColumn
import ru.digitalpunk.lumi.privacy.barScheme
import ru.digitalpunk.lumi.engine.TabGroup
import ru.digitalpunk.lumi.engine.TabId
import ru.digitalpunk.lumi.engine.TabsState
import ru.digitalpunk.lumi.engine.tabsIn
import ru.digitalpunk.lumi.tabs.TabSnapshots

private sealed interface GroupDialog {
    /** [forTab] is set when the group is made from a card's Move to group menu. */
    data class Create(val forTab: TabId?) : GroupDialog
    data class Rename(val group: TabGroup) : GroupDialog
    data class Delete(val group: TabGroup, val tabCount: Int) : GroupDialog
}

/**
 * The tabs of the current group as cards, two to a row on a phone in portrait (more on wider screens), over the page,
 * with a row of group chips on top. A tap on a card selects it; the toolbar has New tab, the count of the current group's tabs and Done.
 */
@Composable
fun TabGrid(
    state: TabsState,
    snapshots: TabSnapshots,
    onSelect: (TabId) -> Unit,
    onClose: (TabId) -> Unit,
    onNewTab: () -> Unit,
    onDone: () -> Unit,
    onSelectGroup: (GroupId?) -> Unit,
    onCreateGroup: (String) -> GroupId?,
    onRenameGroup: (GroupId, String) -> Boolean,
    /** True if a blank tab replaced everything. */
    onDeleteGroup: (GroupId) -> Boolean,
    onMoveTab: (TabId, GroupId?) -> Boolean,
    /** Asks for the system prompt; the Private chip shows the lock until it succeeds. */
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = state.tabsIn(state.currentGroup)
    val privateChip = state.currentGroup == PRIVATE_GROUP
    val locked = privateChip && state.privateLocked
    val count = currentGroupTabCount(state)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    var dialog by remember { mutableStateOf<GroupDialog?>(null) }
    val reserved = stringResource(R.string.group_tabs)

    fun moved(tab: TabId, group: GroupId?) {
        if (!onMoveTab(tab, group)) return
        val name = state.groups.firstOrNull { it.id == group }?.name
        val text = if (name == null) resources.getString(R.string.snackbar_moved_to_tabs) else resources.getString(R.string.snackbar_moved_to_group, name)
        scope.launch { snackbar.showSnackbar(text) }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        GroupChipsRow(
            chips = chips(state),
            currentGroup = state.currentGroup,
            onSelect = onSelectGroup,
            onNew = { dialog = GroupDialog.Create(forTab = null) },
            onRename = { id -> state.groups.firstOrNull { it.id == id }?.let { dialog = GroupDialog.Rename(it) } },
            onDelete = { id ->
                state.groups.firstOrNull { it.id == id }?.let { group ->
                    val count = state.tabsIn(id).size
                    if (deleteNeedsConfirmation(count)) dialog = GroupDialog.Delete(group, count) else onDeleteGroup(group.id)
                }
            },
        )
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (locked) {
                PrivateLockColumn(state.privateCount, onUnlock)
            } else if (privateChip && tabs.isEmpty()) {
                PrivateEmptyColumn(onNewTab)
            } else key(state.currentGroup) {
                // A new chip starts at the selected card (or the top) again.
                val grid = rememberLazyGridState(initialFirstVisibleItemIndex = tabs.indexOfFirst { it.id == state.selectedId }.coerceAtLeast(0))
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 140.dp),
                    state = grid,
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                ) {
                    itemsIndexed(tabs, key = { _, tab -> tab.id.value }) { index, tab ->
                        // A private card is drawn in the fixed dark private scheme, in light and dark theme alike.
                        MaterialTheme(colorScheme = barScheme(MaterialTheme.colorScheme, tab.isPrivate)) {
                        TabCard(
                            tab = tab,
                            position = index + 1,
                            total = tabs.size,
                            selected = tab.id == state.selectedId,
                            snapshots = snapshots,
                            groups = state.groups,
                            onSelect = { onSelect(tab.id) },
                            onClose = { onClose(tab.id) },
                            onMove = { moved(tab.id, it) },
                            onNewGroup = { dialog = GroupDialog.Create(forTab = tab.id) },
                            modifier = Modifier.animateItem(),
                        )
                        }
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
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
                    enabled = !locked,
                    colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    modifier = Modifier.size(48.dp).align(Alignment.CenterStart),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(if (privateChip) R.string.action_new_private_tab else R.string.action_new_tab))
                }
                // While locked the toolbar shows no count.
                Text(
                    when {
                        locked -> ""
                        privateChip -> pluralStringResource(R.plurals.private_tab_count, count, count)
                        else -> pluralStringResource(R.plurals.tab_count, count, count)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Center),
                )
                TextButton(onClick = onDone, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Text(stringResource(R.string.action_done), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
        when (val d = dialog) {
            null -> Unit
            is GroupDialog.Create -> GroupNameDialog(
                initial = null,
                otherNames = state.groups.map { it.name },
                reserved = reserved,
                onConfirm = { name ->
                    dialog = null
                    val id = onCreateGroup(name)
                    if (id != null) {
                        if (d.forTab == null) onSelectGroup(id) else moved(d.forTab, id)
                    }
                },
                onDismiss = { dialog = null },
            )
            is GroupDialog.Rename -> GroupNameDialog(
                initial = d.group.name,
                otherNames = state.groups.filter { it.id != d.group.id }.map { it.name },
                reserved = reserved,
                onConfirm = { name ->
                    dialog = null
                    onRenameGroup(d.group.id, name)
                },
                onDismiss = { dialog = null },
            )
            is GroupDialog.Delete -> DeleteGroupDialog(
                name = d.group.name,
                tabCount = d.tabCount,
                onConfirm = {
                    dialog = null
                    onDeleteGroup(d.group.id)
                },
                onDismiss = { dialog = null },
            )
        }
    }
}
