package ru.digitalpunk.lumi.ui.library

import android.text.format.DateFormat
import android.text.format.Formatter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.data.downloads.DownloadItem
import ru.digitalpunk.lumi.data.downloads.DownloadState
import ru.digitalpunk.lumi.data.downloads.groupDownloadsByDay
import ru.digitalpunk.lumi.data.downloads.isActive
import ru.digitalpunk.lumi.downloads.DownloadManager
import ru.digitalpunk.lumi.downloads.FileOpener
import ru.digitalpunk.lumi.downloads.LineWords
import ru.digitalpunk.lumi.downloads.fileKind
import ru.digitalpunk.lumi.downloads.percent
import ru.digitalpunk.lumi.downloads.spokenPercent
import ru.digitalpunk.lumi.downloads.supportingLine
import ru.digitalpunk.lumi.ui.FileTile
import ru.digitalpunk.lumi.ui.LumiDropdownMenu
import ru.digitalpunk.lumi.ui.LumiSnackbarVisuals
import ru.digitalpunk.lumi.ui.MenuItem
import ru.digitalpunk.lumi.ui.PrivateBadge
import ru.digitalpunk.lumi.ui.tabgrid.DISMISS_FRACTION
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

/** Opens [item] with the system chooser, or tells why it cannot. */
fun openDownload(context: android.content.Context, item: DownloadItem, missing: Boolean, scope: CoroutineScope, snackbar: SnackbarHostState) {
    val problem = if (missing) FileOpener.Result.Gone else FileOpener.open(context, item)
    val text = when (problem) {
        FileOpener.Result.Started -> return
        FileOpener.Result.NoApp -> R.string.download_no_app
        FileOpener.Result.Gone -> R.string.download_file_was_deleted
    }
    scope.launch { snackbar.showSnackbar(context.getString(text)) }
}

/** The Library's Downloads tab (spec section 4): day sections, rows per state, the item menu. [scope] outlives the screen, for Undo. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DownloadsList(downloads: DownloadManager, snackbar: SnackbarHostState, scope: CoroutineScope) {
    val items by downloads.items.collectAsStateWithLifecycle()
    val missing by downloads.missing.collectAsStateWithLifecycle()
    LaunchedEffect(items.size) { downloads.refreshMissing() }
    if (items.isEmpty()) return DownloadsEmpty()
    val context = LocalContext.current
    val resources = LocalResources.current
    val zone = ZoneId.systemDefault()
    val days = remember(items) { groupDownloadsByDay(items, zone) }
    val today = LocalDate.now(zone)
    val timeFormat = DateFormat.getTimeFormat(context)
    val undo = stringResource(R.string.snackbar_action_undo)
    val words = LineWords(
        of = { a, b -> resources.getString(R.string.download_progress, a, b) },
        failed = stringResource(R.string.download_state_failed),
        cancelled = stringResource(R.string.download_state_cancelled),
        private = stringResource(R.string.download_state_private),
        gone = stringResource(R.string.download_file_gone),
    )
    val remove = { item: DownloadItem ->
        downloads.remove(item.id)
        scope.launch {
            if (snackbar.showSnackbar(LumiSnackbarVisuals(resources.getString(R.string.snackbar_download_removed), undo)) == SnackbarResult.ActionPerformed) downloads.restore(item)
        }
        Unit
    }
    // The file goes only when the snackbar ends without Undo (or its coroutine is cancelled).
    val deleteFile = { item: DownloadItem ->
        downloads.remove(item.id)
        scope.launch {
            var undone = false
            try {
                undone = snackbar.showSnackbar(LumiSnackbarVisuals(resources.getString(R.string.download_deleted, item.fileName), undo)) == SnackbarResult.ActionPerformed
            } finally {
                if (undone) downloads.restore(item) else downloads.deleteFile(item)
            }
        }
        Unit
    }
    LazyColumn(Modifier.fillMaxSize()) {
        days.forEach { day ->
            stickyHeader(key = day.date.toString()) { DayHeader(day.date, today) }
            items(day.items, key = { it.id }) { item ->
                val gone = item.id in missing
                DownloadRow(
                    item, gone, timeFormat.format(Date.from(Instant.ofEpochMilli(item.startedAt))), words,
                    onOpen = { openDownload(context, item, gone, scope, snackbar) },
                    onShare = { if (FileOpener.share(context, item) != FileOpener.Result.Started) scope.launch { snackbar.showSnackbar(resources.getString(R.string.download_file_was_deleted)) } },
                    onCancel = { downloads.cancel(item.id) },
                    onRetry = { downloads.retry(item.id) },
                    onRemove = { remove(item) },
                    onDelete = { deleteFile(item) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun DownloadRow(
    item: DownloadItem,
    gone: Boolean,
    time: String,
    words: LineWords,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val active = item.state.isActive
    val failed = item.state == DownloadState.Failed
    val line = supportingLine(item, { Formatter.formatShortFileSize(context, it) }, time, gone, words)
    val pct = percent(item.doneBytes, item.totalBytes)
    val privateLabel = if (item.isPrivate) ", " + stringResource(R.string.cd_download_private) else ""
    val label = when {
        active -> if (pct == null) stringResource(R.string.cd_download_running, item.fileName) else spokenPercent(pct).let { pluralStringResource(R.plurals.download_percent, it, item.fileName, it) }
        item.state == DownloadState.Done -> stringResource(R.string.cd_download_done_row, item.fileName, line)
        else -> "${item.fileName}, $line"
    } + privateLabel
    var menu by remember { mutableStateOf(false) }
    val dismiss = rememberSwipeToDismissBoxState(positionalThreshold = { it * DISMISS_FRACTION })
    LaunchedEffect(dismiss.currentValue) { if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) onRemove() }
    SwipeToDismissBox(
        dismiss,
        enableDismissFromStartToEnd = false,
        gesturesEnabled = !active,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 24.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer) }
        },
    ) {
        Box {
            ListItem(
                headlineContent = { Text(item.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = {
                    Column {
                        Text(line, color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (active) {
                            Spacer(Modifier.height(8.dp))
                            val bar = Modifier.fillMaxWidth().height(4.dp)
                            if (pct == null) LinearProgressIndicator(bar) else LinearProgressIndicator({ pct / 100f }, bar)
                        }
                    }
                },
                leadingContent = {
                    Box {
                        FileTile(fileKind(item.mimeType, item.fileName), 40.dp, error = failed)
                        if (item.isPrivate) Box(Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp)) { PrivateBadge() }
                    }
                },
                trailingContent = {
                    when {
                        active -> IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, stringResource(R.string.cd_download_cancel, item.fileName)) }
                        item.state == DownloadState.Done -> IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_download_more, item.fileName)) }
                        else -> IconButton(onClick = onRetry) { Icon(painterResource(R.drawable.ic_refresh), stringResource(R.string.cd_download_retry, item.fileName)) }
                    }
                },
                modifier = Modifier.heightIn(min = if (active) 88.dp else 72.dp)
                    .semantics {
                        contentDescription = label
                        if (active) liveRegion = LiveRegionMode.Polite
                    }
                    .combinedClickable(enabled = !active, onLongClick = { if (item.state == DownloadState.Done) menu = true }) {
                        if (item.state == DownloadState.Done) onOpen() else onRetry()
                    },
            )
            LumiDropdownMenu(menu, { menu = false }) {
                if (!gone) {
                    MenuItem(stringResource(R.string.action_open), { Icon(painterResource(R.drawable.ic_open_in_new), null) }) { menu = false; onOpen() }
                    MenuItem(stringResource(R.string.link_share), { Icon(Icons.Filled.Share, null) }) { menu = false; onShare() }
                }
                MenuItem(stringResource(R.string.download_remove_from_list), { Icon(painterResource(R.drawable.ic_playlist_remove), null) }) { menu = false; onRemove() }
                if (!gone) MenuItem(stringResource(R.string.download_delete_file), { Icon(Icons.Filled.Delete, null) }) { menu = false; onDelete() }
            }
        }
    }
}

@Composable
private fun DownloadsEmpty() {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(R.drawable.ic_download), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.downloads_empty_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.downloads_empty_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

/** "Clear list" (spec 4.3): entries in progress and every file stay. */
@Composable
fun ClearDownloadsDialog(onClear: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.downloads_clear_title)) },
        text = { Text(stringResource(R.string.downloads_clear_body)) },
        confirmButton = { TextButton(onClick = onClear) { Text(stringResource(R.string.action_clear)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
