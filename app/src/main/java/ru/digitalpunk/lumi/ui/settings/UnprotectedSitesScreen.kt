package ru.digitalpunk.lumi.ui.settings

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.isPrivate
import ru.digitalpunk.lumi.ui.LumiSnackbarVisuals

/**
 * S8 Sites without tracker protection: the hosts the person turned protection off for in the shield sheet. "Turn on
 * protection" removes one, with Undo; the engine is told through a normal tab, as the shield does.
 */
@Composable
fun UnprotectedSitesScreen(
    settings: SettingsStore,
    engine: BrowserEngine,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val hosts by settings.protectionOffHosts.collectAsStateWithLifecycle(emptySet())
    val rows = unprotectedRows(hosts)
    val resources = LocalResources.current
    // A private tab ignores the change, so the selected normal tab, or any normal one, carries it.
    fun normalTab() = engine.tabs.value.let { state ->
        state.tabs.firstOrNull { it.id == state.selectedId && !it.isPrivate } ?: state.tabs.firstOrNull { !it.isPrivate }
    }?.id
    suspend fun setOff(host: String, off: Boolean) {
        settings.setProtectionOff(host, off)
        normalTab()?.let { engine.setProtectionOff(it, host, off) }
    }
    SettingsLazyPage(stringResource(R.string.settings_unprotected), onBack, modifier) {
        if (rows.isEmpty()) {
            item { Note(stringResource(R.string.unprotected_empty)) }
            return@SettingsLazyPage
        }
        item { Note(stringResource(R.string.unprotected_note)) }
        itemsIndexed(rows, key = { _, host -> host }) { index, host ->
            SettingsRowWithTile(host, null, "https://$host/", Modifier.clip(groupShape(index, rows.size))) {
                TextButton(onClick = {
                    scope.launch {
                        setOff(host, false)
                        val undo = LumiSnackbarVisuals(
                            resources.getString(R.string.snackbar_protection_on, host), resources.getString(R.string.snackbar_action_undo),
                        )
                        if (snackbar.showSnackbar(undo) == SnackbarResult.ActionPerformed) setOff(host, true)
                    }
                }) { Text(stringResource(R.string.unprotected_turn_on)) }
            }
        }
    }
}
