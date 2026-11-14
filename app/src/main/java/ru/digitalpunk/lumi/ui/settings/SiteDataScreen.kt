package ru.digitalpunk.lumi.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.data.library.DayKind
import ru.digitalpunk.lumi.data.library.LibraryRepository
import ru.digitalpunk.lumi.data.library.dayKind
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.library.weekdayDate
import ru.digitalpunk.lumi.ui.SiteIconTile
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * S7 Site data: the sites of the history with a search field. The delete button clears the site's cookies, storage,
 * sign-ins and cache at once; the row stays, because the site is still in the history.
 */
@Composable
fun SiteDataScreen(
    library: LibraryRepository,
    engine: BrowserEngine,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val history by library.history.collectAsStateWithLifecycle(emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val all = remember(history) { siteRows(history, "") }
    val rows = remember(history, query) { siteRows(history, query) }
    val resources = LocalResources.current
    val locale = LocalConfiguration.current.locales[0]
    val zone = ZoneId.systemDefault()
    val today = LocalDate.now(zone)
    SettingsLazyPage(stringResource(R.string.settings_site_data), onBack, modifier) {
        if (all.isEmpty()) {
            item { Note(stringResource(R.string.site_data_empty)) }
            return@SettingsLazyPage
        }
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.site_data_search)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                shape = CircleShape,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
            )
        }
        itemsIndexed(rows, key = { _, row -> row.host }) { index, row ->
            val date = Instant.ofEpochMilli(row.lastVisit).atZone(zone).toLocalDate()
            val day = when (dayKind(date, today)) {
                DayKind.Today -> stringResource(R.string.history_today)
                DayKind.Yesterday -> stringResource(R.string.history_yesterday)
                DayKind.Other -> weekdayDate(date, locale)
            }
            val label = stringResource(R.string.cd_delete_site_data, row.host)
            SettingsRowWithTile(
                row.host, stringResource(R.string.site_data_last_visit, day), "https://${row.host}/",
                Modifier.clip(groupShape(index, rows.size)),
            ) {
                IconButton(onClick = {
                    scope.launch {
                        val done = runCatching { engine.clearSiteData(row.host) }.isSuccess
                        snackbar.showSnackbar(
                            if (done) resources.getString(R.string.snackbar_site_data_deleted, row.host) else resources.getString(R.string.snackbar_data_delete_failed),
                        )
                    }
                }) { Icon(Icons.Filled.Delete, contentDescription = label) }
            }
        }
        item { Note(stringResource(R.string.site_data_note), Modifier.padding(top = 14.dp)) }
    }
}

/** A site row: the site's icon tile, the host, a supporting line and a trailing button. */
@Composable
internal fun SettingsRowWithTile(host: String, supporting: String, url: String, modifier: Modifier, trailing: @Composable () -> Unit) {
    ListItem(
        headlineContent = { Text(host) },
        supportingContent = { Text(supporting) },
        leadingContent = { SiteIconTile(url, host, 40.dp, 8.dp, backfill = false) },
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = modifier,
    )
}

/** Small explanatory text under or instead of a list. */
@Composable
internal fun Note(text: String, modifier: Modifier = Modifier) {
    Text(
        text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
