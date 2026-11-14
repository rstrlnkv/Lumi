package ru.digitalpunk.lumi.ui.settings

import androidx.activity.compose.LocalActivity
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import ru.digitalpunk.lumi.R
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.about.Channel
import ru.digitalpunk.lumi.about.installedChannel
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.updates.askUpdateNotifications

@StringRes fun Channel.labelRes(): Int = when (this) {
    Channel.STABLE -> R.string.update_channel_stable
    Channel.BETA -> R.string.update_channel_beta
    Channel.DEV -> R.string.update_channel_dev
}

@StringRes fun Channel.summaryRes(): Int = when (this) {
    Channel.STABLE -> R.string.update_channel_stable_summary
    Channel.BETA -> R.string.update_channel_beta_summary
    Channel.DEV -> R.string.update_channel_dev_summary
}

/** S10 Updates: the daily check's switch and the channel it looks at; the channel row rests while the switch is off. */
@Composable
fun UpdatesSettings(settings: SettingsStore, scope: CoroutineScope, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val on by settings.updatesOn.collectAsStateWithLifecycle(true)
    val stored by settings.updateChannel.collectAsStateWithLifecycle(null)
    val channel = stored?.let(Channel::of) ?: installedChannel
    val activity = LocalActivity.current
    var picking by rememberSaveable { mutableStateOf(false) }
    SettingsPageScaffold(stringResource(R.string.settings_section_updates), onBack, modifier) {
        SettingsGroup {
            SettingsRow(
                stringResource(R.string.settings_updates_check),
                stringResource(R.string.settings_updates_check_summary),
                rememberVectorPainter(Icons.Filled.Notifications),
                Modifier.toggleable(value = on, role = Role.Switch) {
                    scope.launch { settings.setUpdatesOn(it) }
                    if (it) activity?.let(::askUpdateNotifications)
                },
            ) { Switch(checked = on, onCheckedChange = null) }
            SettingsRow(
                stringResource(R.string.settings_update_channel),
                stringResource(channel.labelRes()) + " · " + stringResource(channel.summaryRes()),
                rememberVectorPainter(Icons.Filled.Build),
                Modifier.clickable(enabled = on, onClickLabel = stringResource(R.string.cd_change), role = Role.Button) { picking = true },
            )
        }
    }
    if (picking) {
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text(stringResource(R.string.settings_update_channel)) },
            text = {
                Column(Modifier.selectableGroup()) {
                    // Most stable first.
                    Channel.entries.reversed().forEach { option ->
                        ListItem(
                            headlineContent = { Text(stringResource(option.labelRes())) },
                            supportingContent = { Text(stringResource(option.summaryRes())) },
                            leadingContent = { RadioButton(selected = option == channel, onClick = null) },
                            modifier = Modifier.selectable(selected = option == channel, role = Role.RadioButton) {
                                picking = false
                                scope.launch { settings.setUpdateChannel(option.name) }
                            },
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picking = false }) { Text(stringResource(android.R.string.cancel)) } },
        )
    }
}
