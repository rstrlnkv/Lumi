package ru.digitalpunk.lumi.ui.tabgrid

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.engine.GroupId
import ru.digitalpunk.lumi.engine.TabGroup

/** The menu of a card. Level 1 has Move to group; level 2 lists the shared list and the groups. [startAtGroups] opens level 2 straight away. */
@Composable
fun CardMenu(
    expanded: Boolean,
    startAtGroups: Boolean,
    tabGroup: GroupId?,
    groups: List<TabGroup>,
    onDismiss: () -> Unit,
    onMove: (GroupId?) -> Unit,
    onNewGroup: () -> Unit,
) {
    var level by remember(expanded, startAtGroups) { mutableIntStateOf(if (startAtGroups) 2 else 1) }
    val current = stringResource(R.string.state_current_group)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.width(240.dp)) {
        if (level == 1) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_move_to_group)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_drive_file_move), contentDescription = null) },
                trailingIcon = { Icon(painterResource(R.drawable.ic_arrow_right), contentDescription = null) },
                onClick = { level = 2 },
            )
        } else {
            val moveLabel = stringResource(R.string.action_move_to_group)
            val sharedName = stringResource(R.string.group_tabs)
            DropdownMenuItem(
                text = { Text(moveLabel) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null) },
                onClick = { level = 1 },
            )
            moveRows(groups, tabGroup).forEach { row ->
                DropdownMenuItem(
                    text = { Text(row.group?.name ?: sharedName) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_folder), contentDescription = null) },
                    trailingIcon = if (row.isCurrent) {
                        { Icon(Icons.Filled.Check, contentDescription = null) }
                    } else {
                        null
                    },
                    enabled = !row.isCurrent,
                    onClick = {
                        onDismiss()
                        onMove(row.group?.id)
                    },
                    modifier = if (row.isCurrent) Modifier.semantics { stateDescription = current } else Modifier,
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.action_new_group)) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null) },
                onClick = {
                    onDismiss()
                    onNewGroup()
                },
            )
        }
    }
}
