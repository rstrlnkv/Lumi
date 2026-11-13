package ru.digitalpunk.lumi.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.data.search.SearchEngine

/** The single-choice list of search engines; a tap picks one and the caller closes the list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchEngineScreen(selected: SearchEngine, onPick: (SearchEngine) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(stringResource(R.string.search_engine_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
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
            Column(Modifier.selectableGroup()) {
                SearchEngine.entries.forEach { engine ->
                    ListItem(
                        headlineContent = { Text(engine.displayName()) },
                        supportingContent = { Text(engine.domain) },
                        leadingContent = { RadioButton(selected = engine == selected, onClick = null) },
                        modifier = Modifier.selectable(selected = engine == selected, role = Role.RadioButton) { onPick(engine) },
                    )
                }
            }
            Text(
                stringResource(R.string.search_engine_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

/** The engine's name as shown: a brand name, translated only where the brand has a local form (Яндекс). */
@Composable
fun SearchEngine.displayName(): String = when (this) {
    SearchEngine.Google -> "Google"
    SearchEngine.DuckDuckGo -> "DuckDuckGo"
    SearchEngine.Yandex -> stringResource(R.string.engine_yandex)
    SearchEngine.Bing -> "Bing"
    SearchEngine.Startpage -> "Startpage"
    SearchEngine.Ecosia -> "Ecosia"
}
