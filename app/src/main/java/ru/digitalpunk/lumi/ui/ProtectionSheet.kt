package ru.digitalpunk.lumi.ui

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
import ru.digitalpunk.lumi.address.SiteStatus

/** Site protection for the page on screen: blocked trackers, the connection, and the per-site switch. [off] is the stored choice, also on http pages. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProtectionSheet(host: String, site: SiteStatus, off: Boolean, secure: Boolean, onToggle: (off: Boolean) -> Unit, onDismiss: () -> Unit) {
    val count = (site as? SiteStatus.Protected)?.count ?: 0
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
                        when {
                            off -> stringResource(R.string.trackers_off)
                            count == 0 -> stringResource(R.string.trackers_none)
                            else -> pluralStringResource(R.plurals.trackers_blocked, count, count)
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
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            ListItem(
                headlineContent = { Text(stringResource(R.string.protection_for_site)) },
                supportingContent = { Text(stringResource(if (off) R.string.protection_off_body else R.string.protection_on_body)) },
                trailingContent = { Switch(checked = !off, onCheckedChange = null) },
                colors = colors,
                modifier = Modifier.toggleable(value = !off, role = Role.Switch) { on -> onToggle(!on) },
            )
        }
    }
}
