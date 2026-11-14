package ru.digitalpunk.lumi.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.data.ClearItem
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.data.library.LibraryRepository
import ru.digitalpunk.lumi.engine.BrowserEngine
import java.time.ZoneId

/** S5 Privacy: Delete browsing data (opens the S5a sheet), then two info rows that are text, not buttons. */
@Composable
fun PrivacySettings(
    settings: SettingsStore,
    engine: BrowserEngine,
    library: LibraryRepository,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var sheetOpen by rememberSaveable { mutableStateOf(false) }
    SettingsPageScaffold(stringResource(R.string.settings_privacy), onBack, modifier) {
        SettingsGroup {
            SettingsRow(
                stringResource(R.string.settings_delete_data), stringResource(R.string.settings_delete_data_summary),
                painterResource(R.drawable.ic_delete_sweep), Modifier.clickable(role = Role.Button) { sheetOpen = true },
            )
        }
        SettingsGroup {
            SettingsRow(
                stringResource(R.string.settings_tracker_protection), stringResource(R.string.settings_tracker_protection_summary),
                painterResource(R.drawable.ic_shield),
            )
            SettingsRow(
                stringResource(R.string.settings_private_lock), stringResource(R.string.settings_private_lock_summary),
                rememberVectorPainter(Icons.Filled.Lock),
            )
        }
    }
    if (sheetOpen) {
        val resources = LocalResources.current
        DeleteDataSheet(settings, onDismiss = { sheetOpen = false }) { choice ->
            sheetOpen = false
            scope.launch {
                settings.setClearDataChoice(choice)
                val done = deleteBrowsingData(choice, engine, library, ZoneId.systemDefault())
                snackbar.showSnackbar(resources.getString(if (done) R.string.snackbar_data_deleted else R.string.snackbar_data_delete_failed))
            }
        }
    }
}

/** The boxes as names, so a half-made choice survives rotation. */
private val ChoiceSaver = Saver<Set<ClearItem>, ArrayList<String>>(
    save = { choice -> ArrayList(choice.map { it.name }) },
    restore = { names -> ClearItem.entries.filterTo(mutableSetOf()) { it.name in names } },
)

/** S5a: for all time, four boxes, Cancel and Delete. The sheet is the confirmation; the last choice comes checked. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeleteDataSheet(settings: SettingsStore, onDismiss: () -> Unit, onDelete: (Set<ClearItem>) -> Unit) {
    val stored by settings.clearDataChoice.collectAsStateWithLifecycle<Set<ClearItem>?>(null)
    val initial = stored ?: return
    var choice by rememberSaveable(stateSaver = ChoiceSaver) { mutableStateOf(initial) }
    fun toggle(item: ClearItem, on: Boolean) {
        choice = if (on) choice + item else choice - item
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.settings_delete_data), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                stringResource(R.string.delete_data_line), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            )
            ClearBox(ClearItem.History, R.string.delete_data_history, R.string.delete_data_history_summary, choice, ::toggle)
            ClearBox(ClearItem.Cookies, R.string.delete_data_cookies, R.string.delete_data_cookies_summary, choice, ::toggle)
            ClearBox(ClearItem.Cache, R.string.delete_data_cache, R.string.delete_data_cache_summary, choice, ::toggle)
            ClearBox(ClearItem.Tabs, R.string.delete_data_tabs, R.string.delete_data_tabs_summary, choice, ::toggle)
            Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onDelete(choice) },
                    enabled = canDelete(choice),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(stringResource(R.string.action_delete)) }
            }
        }
    }
}

/** One box as a full row: one toggleable element for TalkBack. */
@Composable
private fun ClearBox(item: ClearItem, title: Int, summary: Int, choice: Set<ClearItem>, onToggle: (ClearItem, Boolean) -> Unit) {
    val on = item in choice
    Row(
        Modifier.fillMaxWidth().toggleable(value = on, role = Role.Checkbox) { onToggle(item, it) }.padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(summary), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked = on, onCheckedChange = null)
    }
}
