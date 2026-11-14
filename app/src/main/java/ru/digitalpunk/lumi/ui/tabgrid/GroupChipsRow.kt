package ru.digitalpunk.lumi.ui.tabgrid

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.engine.GroupId
import ru.digitalpunk.lumi.ui.LumiDropdownMenu
import ru.digitalpunk.lumi.ui.menuItemShape

/** The shared list, one chip per group and a "+" chip, in a row that scrolls sideways. A tap shows that group's tabs; a long press on a group chip opens Rename and Delete. */
@Composable
fun GroupChipsRow(
    chips: List<ChipModel>,
    currentGroup: GroupId?,
    onSelect: (GroupId?) -> Unit,
    onNew: () -> Unit,
    onRename: (GroupId) -> Unit,
    onDelete: (GroupId) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    val selectedAt = chips.indexOfFirst { it.group == currentGroup }.coerceAtLeast(0)
    LaunchedEffect(selectedAt) {
        val info = state.layoutInfo
        val item = info.visibleItemsInfo.firstOrNull { it.index == selectedAt }
        if (item == null || chipNeedsScroll(item.offset, item.size, info.viewportStartOffset, info.viewportEndOffset)) {
            state.animateScrollToItem(selectedAt)
        }
    }
    LazyRow(
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .heightIn(min = 56.dp),
    ) {
        itemsIndexed(chips, key = { _, chip -> chip.group?.value ?: "" }) { _, chip ->
            GroupChip(
                chip = chip,
                selected = chip.group == currentGroup,
                onClick = { onSelect(chip.group) },
                onRename = { chip.group?.let(onRename) },
                onDelete = { chip.group?.let(onDelete) },
            )
        }
        item(key = "new") {
            val label = stringResource(R.string.action_new_group)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clickable(onClick = onNew, role = Role.Button, onClickLabel = label)
                    .semantics { contentDescription = label },
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.size(32.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupChip(chip: ChipModel, selected: Boolean, onClick: () -> Unit, onRename: () -> Unit, onDelete: () -> Unit) {
    val private = chip.kind == ChipKind.Private
    val name = chip.name ?: stringResource(if (private) R.string.group_private else R.string.group_tabs)
    val named = chip.kind == ChipKind.Named
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val timeout = LocalViewConfiguration.current.longPressTimeoutMillis
    val interactions = remember { MutableInteractionSource() }
    var longPressed by remember { mutableStateOf(false) }
    if (named) {
        // FilterChip has no long click, so time the press here. A new press resets the flag, so a cancelled one never swallows the next tap.
        LaunchedEffect(interactions) {
            interactions.interactions.collectLatest {
                if (it is PressInteraction.Press) {
                    longPressed = false
                    delay(timeout)
                    longPressed = true
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    menu = true
                }
            }
        }
    }
    // The private count is read only while unlocked (it is 0 while locked), and never shown on the chip.
    val description = if (private && chip.count == 0) {
        stringResource(R.string.cd_private_chip)
    } else {
        pluralStringResource(R.plurals.cd_group_chip, chip.count, name, chip.count)
    }
    val renameLabel = stringResource(R.string.action_rename_group)
    val deleteLabel = stringResource(R.string.action_delete_group)
    Box {
        FilterChip(
            selected = selected,
            onClick = {
                if (longPressed) longPressed = false else onClick()
            },
            interactionSource = interactions,
            label = { Text(if (private) name else stringResource(R.string.group_chip_label, name, chip.count), modifier = Modifier.clearAndSetSemantics {}) },
            leadingIcon = if (selected) {
                { Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else {
                null
            },
            modifier = Modifier.semantics {
                contentDescription = description
                if (named) {
                    customActions = listOf(
                        CustomAccessibilityAction(renameLabel) { onRename(); true },
                        CustomAccessibilityAction(deleteLabel) { onDelete(); true },
                    )
                }
            },
        )
        LumiDropdownMenu(menu, { menu = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_rename)) },
                leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                onClick = {
                    menu = false
                    onRename()
                },
                modifier = Modifier.menuItemShape(),
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_delete_group)) },
                leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                colors = MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                ),
                onClick = {
                    menu = false
                    onDelete()
                },
                modifier = Modifier.menuItemShape(),
            )
        }
    }
}
