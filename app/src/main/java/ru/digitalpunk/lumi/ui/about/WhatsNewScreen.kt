package ru.digitalpunk.lumi.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.about.Release
import ru.digitalpunk.lumi.about.parseChangelog

/** "What's new": the releases of CHANGELOG.md (copied into the assets at build time), newest first and open. */
@Composable
internal fun WhatsNewScreen(onBack: () -> Unit, modifier: Modifier) {
    val context = LocalContext.current
    val releases by produceState<List<Release>?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { context.assets.open("CHANGELOG.md").bufferedReader().use { parseChangelog(it.readText()) } }.getOrDefault(emptyList())
        }
    }
    // Expanded versions; null until the user toggles one, which means "only the newest".
    var expanded by rememberSaveable { mutableStateOf<List<String>?>(null) }
    val dates = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
    AboutScaffold(stringResource(R.string.about_whats_new), onBack, modifier) {
        val list = releases ?: return@AboutScaffold
        if (list.isEmpty()) {
            Text(stringResource(R.string.whats_new_empty), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@AboutScaffold
        }
        val open = expanded ?: listOf(list.first().version)
        LazyColumn(Modifier.fillMaxSize()) {
            items(list, key = { it.version }) { release ->
                val isOpen = release.version in open
                Column {
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 72.dp)
                            .clickable(
                                onClickLabel = stringResource(if (isOpen) R.string.cd_collapse else R.string.cd_expand),
                                role = Role.Button,
                            ) { expanded = if (isOpen) open - release.version else open + release.version }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(release.version, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            release.date?.let {
                                Text(dates.format(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Icon(if (isOpen) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }
                    if (isOpen) {
                        Column(Modifier.padding(start = 40.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            release.items.forEach { Text("•  $it", fontSize = 14.sp) }
                        }
                    }
                }
            }
        }
    }
}
