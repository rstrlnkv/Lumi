package ru.digitalpunk.lumi.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.data.search.SearchEngine

/** S3 Search: the engine (opens the engine list through [onPickEngine]) and the suggestions switch. */
@Composable
fun SearchSettings(settings: SettingsStore, scope: CoroutineScope, onPickEngine: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val engine by settings.searchEngine.collectAsStateWithLifecycle(SearchEngine.Google)
    val suggestions by settings.searchSuggestions.collectAsStateWithLifecycle(true)
    SettingsPageScaffold(stringResource(R.string.settings_section_search), onBack, modifier) {
        SettingsGroup {
            SettingsRow(
                stringResource(R.string.settings_search_engine), engine.displayName(), painterResource(R.drawable.ic_travel_explore),
                Modifier.clickable(onClickLabel = stringResource(R.string.cd_change), role = Role.Button, onClick = onPickEngine),
            )
            // One element for TalkBack: headline, summary, then the switch state.
            SettingsRow(
                stringResource(R.string.settings_search_suggestions),
                stringResource(R.string.settings_search_suggestions_summary, engine.displayName()),
                rememberVectorPainter(Icons.Filled.Search),
                Modifier.toggleable(value = suggestions, role = Role.Switch) { scope.launch { settings.setSearchSuggestions(it) } },
            ) { Switch(checked = suggestions, onCheckedChange = null) }
        }
    }
}
