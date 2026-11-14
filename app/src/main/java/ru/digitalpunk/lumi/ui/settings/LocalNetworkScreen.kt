package ru.digitalpunk.lumi.ui.settings

import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.LnaSite

/** S9 Local network access: the stored answers to "Site wants to find devices on your network"; a reset makes the site ask again. */
@Composable
fun LocalNetworkScreen(engine: BrowserEngine, scope: CoroutineScope, onBack: () -> Unit, modifier: Modifier = Modifier) {
    var version by remember { mutableIntStateOf(0) }
    val sites by produceState<List<LnaSite>?>(null, version) { value = runCatching { engine.storedLnaAnswers() }.getOrDefault(emptyList()) }
    SettingsLazyPage(stringResource(R.string.settings_local_network), onBack, modifier) {
        val rows = sites ?: return@SettingsLazyPage
        if (rows.isEmpty()) {
            item { Note(stringResource(R.string.local_network_empty)) }
            return@SettingsLazyPage
        }
        item { Note(stringResource(R.string.local_network_note)) }
        itemsIndexed(rows, key = { _, site -> site.site + " " + site.via.orEmpty() }) { index, site ->
            val answer = stringResource(if (site.allowed) R.string.local_network_allowed else R.string.local_network_blocked)
            val line = site.via?.let { stringResource(R.string.local_network_via, answer, it) } ?: answer
            val label = stringResource(R.string.cd_reset_local_network, site.site)
            SettingsRowWithTile(site.site, line, "https://${site.site}/", Modifier.clip(groupShape(index, rows.size))) {
                IconButton(onClick = {
                    scope.launch {
                        runCatching { engine.resetLna(site) }
                        version++
                    }
                }) { Icon(Icons.Filled.Close, contentDescription = label) }
            }
        }
    }
}
