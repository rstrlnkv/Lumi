package ru.digitalpunk.lumi.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.data.library.LibraryIds
import ru.digitalpunk.lumi.data.library.LibraryRepository
import ru.digitalpunk.lumi.data.library.effectiveStartFolder
import ru.digitalpunk.lumi.ui.library.FolderPickerDialog
import ru.digitalpunk.lumi.ui.library.folderLabel

/** S4 Tabs: the start-page folder (Favorites when the chosen one is gone) and "Switch to new tabs". */
@Composable
fun TabsSettings(settings: SettingsStore, library: LibraryRepository, scope: CoroutineScope, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val nodes by library.nodes.collectAsStateWithLifecycle(emptyList())
    val stored by settings.startPageFolder.collectAsStateWithLifecycle(LibraryIds.FAVORITES)
    val startFolder = effectiveStartFolder(nodes, stored)
    val switchToNewTabs by settings.switchToNewTabs.collectAsStateWithLifecycle(false)
    var picking by rememberSaveable { mutableStateOf(false) }
    SettingsPageScaffold(stringResource(R.string.settings_section_tabs), onBack, modifier) {
        SettingsGroup {
            SettingsRow(
                stringResource(R.string.settings_start_page_folder), folderLabel(nodes, startFolder), painterResource(R.drawable.ic_grid_view),
                Modifier.clickable(onClickLabel = stringResource(R.string.cd_change), role = Role.Button) { picking = true },
            )
            // One element for TalkBack: headline, summary, then the switch state.
            SettingsRow(
                stringResource(R.string.settings_switch_to_new_tab),
                stringResource(R.string.settings_switch_to_new_tab_summary),
                painterResource(R.drawable.ic_tab),
                Modifier.toggleable(value = switchToNewTabs, role = Role.Switch) { scope.launch { settings.setSwitchToNewTabs(it) } },
            ) { Switch(checked = switchToNewTabs, onCheckedChange = null) }
        }
    }
    if (picking) {
        FolderPickerDialog(
            library, nodes, stringResource(R.string.settings_start_page_folder),
            selected = startFolder,
            disabled = emptySet(),
            onPick = { picking = false; scope.launch { settings.setStartPageFolder(it) } },
            onDismiss = { picking = false },
            includeRoot = false,
        )
    }
}
