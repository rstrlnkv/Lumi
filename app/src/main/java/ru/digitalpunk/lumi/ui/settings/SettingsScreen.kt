package ru.digitalpunk.lumi.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R

/** The Settings screen over the browser. One section, Tabs, with "Switch to new tabs". State is the caller's. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(switchToNewTabs: Boolean, onSwitchToNewTabsChange: (Boolean) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(R.string.settings_section_tabs),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(44.dp).padding(start = 16.dp).wrapContentHeight(Alignment.CenterVertically)
                    .semantics { heading() },
            )
            // One element for TalkBack: headline, summary, then the switch state.
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_switch_to_new_tab)) },
                supportingContent = { Text(stringResource(R.string.settings_switch_to_new_tab_summary)) },
                leadingContent = { Icon(painterResource(R.drawable.ic_tab), contentDescription = null) },
                trailingContent = { Switch(checked = switchToNewTabs, onCheckedChange = null) },
                modifier = Modifier.toggleable(value = switchToNewTabs, role = Role.Switch, onValueChange = onSwitchToNewTabsChange),
            )
        }
    }
}
