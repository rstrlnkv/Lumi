package ru.digitalpunk.lumi.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.data.SettingsStore

/** S6 Sites: Site data, Sites without tracker protection (with their count) and Local network access. */
@Composable
fun SitesSettings(settings: SettingsStore, open: (SettingsPage) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val offHosts by settings.protectionOffHosts.collectAsStateWithLifecycle(emptySet())
    SettingsPageScaffold(stringResource(R.string.settings_sites), onBack, modifier) {
        SettingsGroup {
            SettingsRow(
                stringResource(R.string.settings_site_data), stringResource(R.string.settings_site_data_summary),
                painterResource(R.drawable.ic_database), Modifier.clickable(role = Role.Button) { open(SettingsPage.SiteData) },
            )
            SettingsRow(
                stringResource(R.string.settings_unprotected),
                if (offHosts.isEmpty()) stringResource(R.string.settings_unprotected_none)
                else pluralStringResource(R.plurals.settings_site_count, offHosts.size, offHosts.size),
                painterResource(R.drawable.ic_remove_moderator), Modifier.clickable(role = Role.Button) { open(SettingsPage.Unprotected) },
            )
            SettingsRow(
                stringResource(R.string.settings_local_network), stringResource(R.string.settings_local_network_none),
                painterResource(R.drawable.ic_lan), Modifier.clickable(role = Role.Button) { open(SettingsPage.LocalNetwork) },
            )
        }
    }
}
