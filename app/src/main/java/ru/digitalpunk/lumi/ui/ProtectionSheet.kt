package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.address.TrackersRow
import ru.digitalpunk.lumi.address.trackersRow

/**
 * Site protection for the page on screen: blocked trackers, the connection, and the per-site switch. [off] is the stored
 * choice, also on http pages; [trackers] is the real count on https and http alike. A [private] tab has no switch: it always runs strict.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionSheet(
    host: String,
    trackers: Int,
    off: Boolean,
    secure: Boolean,
    private: Boolean,
    onToggle: (off: Boolean) -> Unit,
    onDeleteData: (() -> Unit)?,
    onDismiss: () -> Unit,
) {
    val row = trackersRow(off, trackers)
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text(
                host,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            ListItem(
                leadingContent = {
                    Icon(
                        painterResource(if (off) R.drawable.ic_remove_moderator else R.drawable.ic_shield),
                        contentDescription = null,
                        tint = if (off) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                },
                headlineContent = {
                    Text(
                        when (row) {
                            TrackersRow.Off -> stringResource(R.string.trackers_off)
                            TrackersRow.None -> stringResource(R.string.trackers_none)
                            is TrackersRow.Count -> pluralStringResource(R.plurals.trackers_blocked, row.count, row.count)
                        },
                    )
                },
                supportingContent = { Text(stringResource(R.string.trackers_scope)) },
                colors = colors,
            )
            ListItem(
                leadingContent = {
                    if (secure) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Icon(painterResource(R.drawable.ic_no_encryption), contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    }
                },
                headlineContent = { Text(stringResource(if (secure) R.string.connection_secure else R.string.connection_insecure)) },
                supportingContent = { Text(stringResource(if (secure) R.string.connection_secure_body else R.string.connection_insecure_body)) },
                colors = colors,
            )
            if (private) return@Column
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListItem(
                headlineContent = { Text(stringResource(R.string.protection_for_site)) },
                supportingContent = { Text(stringResource(if (off) R.string.protection_off_body else R.string.protection_on_body)) },
                trailingContent = { Switch(checked = !off, onCheckedChange = null) },
                colors = colors,
                modifier = Modifier.toggleable(value = !off, role = Role.Switch) { on -> onToggle(!on) },
            )
            // Null in a private tab: its data ends with the private session anyway.
            onDeleteData?.let { delete ->
                ListItem(
                    leadingContent = { Icon(painterResource(R.drawable.ic_delete_sweep), contentDescription = null) },
                    headlineContent = { Text(stringResource(R.string.shield_delete_site_data)) },
                    colors = colors,
                    modifier = Modifier.clickable(role = Role.Button, onClick = delete),
                )
            }
        }
    }
}
