package ru.digitalpunk.lumi.ui.library

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.address.displayUrl
import ru.digitalpunk.lumi.data.LibraryTab
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.data.library.BookmarkNodeEntity
import ru.digitalpunk.lumi.data.library.DayKind
import ru.digitalpunk.lumi.data.library.HistoryRow
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import ru.digitalpunk.lumi.data.library.LibraryRepository
import ru.digitalpunk.lumi.data.library.bookmarkCount
import ru.digitalpunk.lumi.data.library.canDelete
import ru.digitalpunk.lumi.data.library.children
import ru.digitalpunk.lumi.data.library.dayKind
import ru.digitalpunk.lumi.data.library.groupByDay
import ru.digitalpunk.lumi.data.library.searchHistory
import ru.digitalpunk.lumi.library.LibraryBack
import ru.digitalpunk.lumi.library.backTarget
import ru.digitalpunk.lumi.library.disabledMoveTargets
import ru.digitalpunk.lumi.library.folderName
import ru.digitalpunk.lumi.library.weekdayDate
import ru.digitalpunk.lumi.library.withoutScheme
import ru.digitalpunk.lumi.ui.LinkAction
import ru.digitalpunk.lumi.ui.LumiDropdownMenu
import ru.digitalpunk.lumi.ui.LumiSnackbarVisuals
import ru.digitalpunk.lumi.ui.MenuItem
import ru.digitalpunk.lumi.ui.tabgrid.DISMISS_FRACTION
import ru.digitalpunk.lumi.ui.tabgrid.cardInitial
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date

private const val SEARCH_DEBOUNCE_MILLIS = 150L

/** Removes [id] and its subtree, then offers Undo. A caller that shows the removed folder moves up itself. */
suspend fun deleteWithUndo(library: LibraryRepository, id: String, snackbar: SnackbarHostState, message: String, undo: String) {
    val removed = library.delete(id) ?: return
    if (snackbar.showSnackbar(LumiSnackbarVisuals(message, undo)) == SnackbarResult.ActionPerformed) library.restore(removed)
}

/**
 * The Library over the browser: Bookmarks and History. [scope] outlives the screen, so an Undo still works after it closes.
 * [onOpen] loads in the current tab (a private tab stays private); [onOpenInTab] takes the link actions of the link sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    library: LibraryRepository,
    settings: SettingsStore,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    inPrivateTab: Boolean,
    onOpen: (String) -> Unit,
    onOpenInTab: (String, LinkAction) -> Unit,
    onEdit: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nodes by library.nodes.collectAsStateWithLifecycle(emptyList())
    val tab by settings.libraryTab.collectAsStateWithLifecycle(LibraryTab.Bookmarks)
    var folderId by rememberSaveable { mutableStateOf(ROOT) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var newFolderIn by rememberSaveable { mutableStateOf<String?>(null) }
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var moving by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var clearing by rememberSaveable { mutableStateOf(false) }
    var folderMenu by remember { mutableStateOf(false) }

    BackHandler {
        when (val back = backTarget(folderId, searching, nodes)) {
            LibraryBack.CloseSearch -> { searching = false; query = "" }
            is LibraryBack.Up -> folderId = back.folder
            LibraryBack.Close -> onClose()
        }
    }

    val bookmarkDeleted = stringResource(R.string.snackbar_bookmark_deleted)
    val folderDeleted = stringResource(R.string.snackbar_folder_deleted)
    val undo = stringResource(R.string.snackbar_action_undo)
    val delete: (String) -> Unit = { id ->
        val node = nodes.firstOrNull { it.id == id }
        when {
            node == null || !canDelete(id) -> Unit
            node.isFolder && bookmarkCount(nodes, id) > 0 -> confirmDelete = id
            else -> {
                if (id == folderId) folderId = node.parentId
                scope.launch { deleteWithUndo(library, id, snackbar, if (node.isFolder) folderDeleted else bookmarkDeleted, undo) }
            }
        }
    }
    val newTabAction = if (inPrivateTab) LinkAction.OpenInNewPrivateTab else LinkAction.OpenInNewTab
    val inFolder = tab == LibraryTab.Bookmarks && folderId != ROOT

    Scaffold(
        modifier = modifier,
        topBar = {
            val focus = remember { FocusRequester() }
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = {
                        when (val back = backTarget(folderId, searching, nodes)) {
                            LibraryBack.CloseSearch -> { searching = false; query = "" }
                            is LibraryBack.Up -> folderId = back.folder
                            LibraryBack.Close -> onClose()
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
                },
                title = {
                    if (searching) {
                        LaunchedEffect(Unit) { focus.requestFocus() }
                        TextField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = { Text(stringResource(R.string.history_search_hint)) },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            modifier = Modifier.fillMaxWidth().focusRequester(focus),
                        )
                    } else {
                        Text(
                            if (inFolder) folderLabel(nodes, folderId) else stringResource(R.string.library_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.semantics { heading() },
                        )
                    }
                },
                actions = {
                    when {
                        searching -> if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, stringResource(R.string.cd_clear_search)) }
                        }
                        tab == LibraryTab.Bookmarks -> {
                            IconButton(onClick = { newFolderIn = folderId }) {
                                Icon(painterResource(R.drawable.ic_create_new_folder), stringResource(R.string.cd_new_folder))
                            }
                            if (inFolder) {
                                Box {
                                    IconButton(onClick = { folderMenu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.cd_folder_options)) }
                                    FolderMenu(folderMenu, folderId, { folderMenu = false }, { renaming = it }, { moving = it }, delete)
                                }
                            }
                        }
                        else -> {
                            IconButton(onClick = { searching = true }) { Icon(Icons.Filled.Search, stringResource(R.string.cd_search_history)) }
                            IconButton(onClick = { clearing = true }) {
                                Icon(painterResource(R.drawable.ic_delete_sweep), stringResource(R.string.cd_clear_history))
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (!searching && !inFolder) {
                PrimaryTabRow(selectedTabIndex = tab.ordinal) {
                    LibraryTab.entries.forEach { entry ->
                        Tab(
                            selected = tab == entry,
                            onClick = { scope.launch { settings.setLibraryTab(entry) } },
                            text = {
                                Text(stringResource(if (entry == LibraryTab.Bookmarks) R.string.library_tab_bookmarks else R.string.library_tab_history))
                            },
                        )
                    }
                }
            }
            when (tab) {
                LibraryTab.Bookmarks -> BookmarksList(
                    nodes, folderId,
                    onOpenFolder = { folderId = it },
                    onOpen = onOpen,
                    bookmarkMenu = { node, expanded, dismiss ->
                        LumiDropdownMenu(expanded, dismiss) {
                            MenuItem(stringResource(if (inPrivateTab) R.string.link_open_new_private_tab else R.string.link_open_new_tab), { Icon(painterResource(R.drawable.ic_open_in_new), null) }) {
                                dismiss(); onOpenInTab(node.url.orEmpty(), newTabAction)
                            }
                            if (!inPrivateTab) {
                                MenuItem(stringResource(R.string.link_open_private_tab), { Icon(painterResource(R.drawable.ic_domino_mask), null) }) {
                                    dismiss(); onOpenInTab(node.url.orEmpty(), LinkAction.OpenInPrivateTab)
                                }
                            }
                            MenuItem(stringResource(R.string.action_edit), { Icon(Icons.Filled.Edit, null) }) { dismiss(); onEdit(node.id) }
                            MenuItem(stringResource(R.string.action_move), { Icon(painterResource(R.drawable.ic_drive_file_move), null) }) { dismiss(); moving = node.id }
                            MenuItem(stringResource(R.string.action_delete), { Icon(Icons.Filled.Delete, null) }) { dismiss(); delete(node.id) }
                        }
                    },
                    folderMenu = { node, expanded, dismiss -> FolderMenu(expanded, node.id, dismiss, { renaming = it }, { moving = it }, delete) },
                )
                LibraryTab.History -> HistoryList(library, snackbar, scope, if (searching) query else "", onOpen) { row, expanded, dismiss, remove ->
                    LumiDropdownMenu(expanded, dismiss) {
                        MenuItem(stringResource(if (inPrivateTab) R.string.link_open_new_private_tab else R.string.link_open_new_tab), { Icon(painterResource(R.drawable.ic_open_in_new), null) }) {
                            dismiss(); onOpenInTab(row.url, newTabAction)
                        }
                        if (!inPrivateTab) {
                            MenuItem(stringResource(R.string.link_open_private_tab), { Icon(painterResource(R.drawable.ic_domino_mask), null) }) {
                                dismiss(); onOpenInTab(row.url, LinkAction.OpenInPrivateTab)
                            }
                        }
                        MenuItem(stringResource(R.string.action_delete), { Icon(Icons.Filled.Delete, null) }) { dismiss(); remove() }
                    }
                }
            }
        }
    }

    newFolderIn?.let { parent ->
        FolderNameDialog(
            stringResource(R.string.folder_new), folderLabel(nodes, parent), "", stringResource(R.string.action_create),
            onConfirm = { name -> newFolderIn = null; scope.launch { library.createFolder(parent, name) } },
            onDismiss = { newFolderIn = null },
        )
    }
    renaming?.let { id ->
        FolderNameDialog(
            stringResource(R.string.folder_rename_title), null, folderLabel(nodes, id), stringResource(R.string.action_save),
            onConfirm = { name -> renaming = null; scope.launch { library.renameFolder(id, name) } },
            onDismiss = { renaming = null },
        )
    }
    moving?.let { id ->
        FolderPickerDialog(
            library, nodes, stringResource(R.string.folder_picker_move_title),
            selected = nodes.firstOrNull { it.id == id }?.parentId ?: ROOT,
            disabled = disabledMoveTargets(nodes, id),
            onPick = { target -> moving = null; scope.launch { library.moveNode(id, target) } },
            onDismiss = { moving = null },
        )
    }
    confirmDelete?.let { id ->
        DeleteFolderDialog(
            folderLabel(nodes, id), bookmarkCount(nodes, id),
            onConfirm = {
                confirmDelete = null
                if (id == folderId) folderId = nodes.firstOrNull { it.id == id }?.parentId ?: ROOT
                scope.launch { deleteWithUndo(library, id, snackbar, folderDeleted, undo) }
            },
            onDismiss = { confirmDelete = null },
        )
    }
    if (clearing) {
        val cleared = stringResource(R.string.history_cleared)
        ClearHistoryDialog(
            onClear = { range ->
                clearing = false
                snackbar.currentSnackbarData?.dismiss()
                scope.launch {
                    library.clearHistory(range, ZoneId.systemDefault())
                    snackbar.showSnackbar(LumiSnackbarVisuals(cleared))
                }
            },
            onDismiss = { clearing = false },
        )
    }
}

@Composable
private fun FolderMenu(
    expanded: Boolean,
    id: String,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit,
    onMove: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    LumiDropdownMenu(expanded, onDismiss) {
        MenuItem(stringResource(R.string.action_rename), { Icon(Icons.Filled.Edit, null) }) { onDismiss(); onRename(id) }
        if (canDelete(id)) {
            MenuItem(stringResource(R.string.action_move), { Icon(painterResource(R.drawable.ic_drive_file_move), null) }) { onDismiss(); onMove(id) }
            MenuItem(stringResource(R.string.action_delete), { Icon(Icons.Filled.Delete, null) }) { onDismiss(); onDelete(id) }
        }
    }
}

/** A 40 dp tile with the first letter of [title]; M7a2 puts the site icon here. */
@Composable
fun LetterTile(title: String) {
    Box(
        Modifier.size(40.dp).background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) { Text(cardInitial(title), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun EmptyState(icon: Int, text: String) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(painterResource(icon), null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookmarksList(
    nodes: List<BookmarkNodeEntity>,
    folderId: String,
    onOpenFolder: (String) -> Unit,
    onOpen: (String) -> Unit,
    bookmarkMenu: @Composable (BookmarkNodeEntity, Boolean, () -> Unit) -> Unit,
    folderMenu: @Composable (BookmarkNodeEntity, Boolean, () -> Unit) -> Unit,
) {
    val rows = children(nodes, folderId)
    if (rows.isEmpty()) return EmptyState(R.drawable.ic_folder, stringResource(R.string.folder_empty))
    val favorites = stringResource(R.string.folder_favorites)
    LazyColumn(Modifier.fillMaxSize()) {
        items(rows, key = { it.id }) { node ->
            var menu by remember { mutableStateOf(false) }
            Box {
                if (node.isFolder) {
                    val count = bookmarkCount(nodes, node.id)
                    // The start-page folder is Favorites until M7a2 lets the person choose another.
                    val supporting = pluralStringResource(if (node.id == FAVORITES) R.plurals.bookmarks_count_start else R.plurals.bookmarks_count, count, count)
                    ListItem(
                        headlineContent = { Text(folderName(node, favorites), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(supporting) },
                        leadingContent = { Icon(painterResource(if (node.id == FAVORITES) R.drawable.ic_folder_special else R.drawable.ic_folder), null) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) },
                        modifier = Modifier.combinedClickable(onLongClick = { menu = true }) { onOpenFolder(node.id) },
                    )
                    folderMenu(node, menu) { menu = false }
                } else {
                    val url = node.url.orEmpty()
                    ListItem(
                        headlineContent = { Text(node.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = { Text(withoutScheme(url), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingContent = { LetterTile(node.title) },
                        trailingContent = {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more)) }
                        },
                        modifier = Modifier.combinedClickable(onLongClick = { menu = true }) { onOpen(url) },
                    )
                    bookmarkMenu(node, menu) { menu = false }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryList(
    library: LibraryRepository,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    query: String,
    onOpen: (String) -> Unit,
    rowMenu: @Composable (HistoryRow, Boolean, () -> Unit, () -> Unit) -> Unit,
) {
    val all by library.history.collectAsStateWithLifecycle(emptyList())
    var filter by remember { mutableStateOf(query) }
    LaunchedEffect(query) {
        if (query.isNotEmpty()) delay(SEARCH_DEBOUNCE_MILLIS)
        filter = query
    }
    val zone = ZoneId.systemDefault()
    val days = remember(all, filter) { groupByDay(searchHistory(all, filter), zone) }
    if (days.isEmpty()) {
        return if (filter.isBlank()) EmptyState(R.drawable.ic_history, stringResource(R.string.history_empty))
        else EmptyState(R.drawable.ic_history, stringResource(R.string.history_no_results))
    }
    val removed = stringResource(R.string.history_removed)
    val undo = stringResource(R.string.snackbar_action_undo)
    val today = LocalDate.now(zone)
    val locale = LocalConfiguration.current.locales[0]
    val timeFormat = DateFormat.getTimeFormat(LocalContext.current)
    val todayText = stringResource(R.string.history_today)
    val yesterdayText = stringResource(R.string.history_yesterday)
    LazyColumn(Modifier.fillMaxSize()) {
        days.forEach { day ->
            stickyHeader(key = day.date.toString()) {
                Text(
                    when (dayKind(day.date, today)) {
                        DayKind.Today -> todayText
                        DayKind.Yesterday -> yesterdayText
                        DayKind.Other -> weekdayDate(day.date, locale)
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().height(44.dp).background(MaterialTheme.colorScheme.surface)
                        .padding(start = 16.dp).wrapContentHeight(Alignment.CenterVertically).semantics { heading() },
                )
            }
            items(day.rows, key = { it.ids.first() }) { row ->
                val remove = {
                    val rows = all.filter { it.id in row.ids }
                    scope.launch {
                        library.deleteVisits(row.ids)
                        if (snackbar.showSnackbar(LumiSnackbarVisuals(removed, undo)) == SnackbarResult.ActionPerformed) library.restoreVisits(rows)
                    }
                    Unit
                }
                val dismiss = rememberSwipeToDismissBoxState(positionalThreshold = { it * DISMISS_FRACTION })
                LaunchedEffect(dismiss.currentValue) { if (dismiss.currentValue == SwipeToDismissBoxValue.EndToStart) remove() }
                var menu by remember { mutableStateOf(false) }
                SwipeToDismissBox(
                    dismiss,
                    enableDismissFromStartToEnd = false,
                    backgroundContent = {
                        Box(
                            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(end = 24.dp),
                            contentAlignment = Alignment.CenterEnd,
                        ) { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.onErrorContainer) }
                    },
                ) {
                    Box {
                        val host = displayUrl(row.url).text
                        val time = timeFormat.format(Date.from(Instant.ofEpochMilli(row.visitedAt)))
                        ListItem(
                            headlineContent = { Text(row.title.ifEmpty { host }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(stringResource(R.string.history_row_support, host, time), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { LetterTile(row.title.ifEmpty { host }) },
                            modifier = Modifier.combinedClickable(onLongClick = { menu = true }) { onOpen(row.url) },
                        )
                        rowMenu(row, menu, { menu = false }, remove)
                    }
                }
            }
        }
    }
}
