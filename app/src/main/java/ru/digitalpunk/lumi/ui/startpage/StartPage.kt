package ru.digitalpunk.lumi.ui.startpage

import android.content.res.Configuration
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.address.displayUrl
import ru.digitalpunk.lumi.data.library.BookmarkNodeEntity
import ru.digitalpunk.lumi.data.library.LibraryRepository
import ru.digitalpunk.lumi.data.library.bookmarkCount
import ru.digitalpunk.lumi.startpage.Tile
import ru.digitalpunk.lumi.startpage.TileAction
import ru.digitalpunk.lumi.startpage.currentFolder
import ru.digitalpunk.lumi.startpage.gridColumns
import ru.digitalpunk.lumi.startpage.tileMenu
import ru.digitalpunk.lumi.startpage.tiles
import ru.digitalpunk.lumi.ui.LinkAction
import ru.digitalpunk.lumi.ui.LumiDropdownMenu
import ru.digitalpunk.lumi.ui.MenuItem
import ru.digitalpunk.lumi.ui.SiteIconTile
import ru.digitalpunk.lumi.ui.library.DeleteFolderDialog
import ru.digitalpunk.lumi.ui.library.FolderNameDialog
import ru.digitalpunk.lumi.ui.library.deleteWithUndo
import ru.digitalpunk.lumi.ui.library.folderLabel

/**
 * The start page over a blank tab: the tiles of the start folder ([start]) or of the folder open in [stack].
 * It swallows touches so nothing reaches the blank page under it. [scope] outlives it, so Undo works after it goes.
 */
@Composable
fun StartPage(
    library: LibraryRepository,
    nodes: List<BookmarkNodeEntity>,
    start: String,
    stack: List<String>,
    inPrivateTab: Boolean,
    bottomInset: Dp,
    snackbar: SnackbarHostState,
    scope: CoroutineScope,
    onOpenFolder: (String) -> Unit,
    onUp: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onOpenInTab: (String, LinkAction) -> Unit,
    onEdit: (String) -> Unit,
    showDefaultCard: Boolean,
    onDefaultNotNow: () -> Unit,
    onDefaultSet: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val folder = currentFolder(stack, start)
    // The default-browser card sits above the start folder only, never inside a sub-folder.
    val card = showDefaultCard && stack.isEmpty()
    val items = tiles(nodes, folder, stringResource(R.string.folder_favorites))
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    val bookmarkDeleted = stringResource(R.string.snackbar_bookmark_deleted)
    val folderDeleted = stringResource(R.string.snackbar_folder_deleted)
    val undo = stringResource(R.string.snackbar_action_undo)
    val run: (Tile, TileAction) -> Unit = { tile, action ->
        val url = (tile as? Tile.Bookmark)?.url.orEmpty()
        when (action) {
            TileAction.OpenInNewTab -> onOpenInTab(url, LinkAction.OpenInNewTab)
            TileAction.OpenInNewPrivateTab -> onOpenInTab(url, LinkAction.OpenInNewPrivateTab)
            TileAction.OpenInPrivateTab -> onOpenInTab(url, LinkAction.OpenInPrivateTab)
            TileAction.Edit -> onEdit(tile.id)
            TileAction.OpenFolder -> onOpenFolder(tile.id)
            TileAction.Rename -> renaming = tile.id
            TileAction.DeleteFolder -> if ((tile as Tile.Folder).count > 0) confirmDelete = tile.id
                else scope.launch { deleteWithUndo(library, tile.id, snackbar, folderDeleted, undo) }
            TileAction.DeleteBookmark -> scope.launch { deleteWithUndo(library, tile.id, snackbar, bookmarkDeleted, undo) }
        }
    }

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).pointerInput(Unit) {}) {
        // An empty start folder is a plain page: the address field has the focus.
        if (stack.isNotEmpty() || items.isNotEmpty() || card) {
            LazyVerticalGrid(
                GridCells.Fixed(gridColumns(landscape)),
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomInset),
            ) {
                if (card) {
                    item(key = "default_card", span = { GridItemSpan(maxLineSpan) }) {
                        DefaultBrowserCard(onDefaultNotNow, onDefaultSet, Modifier.padding(top = 16.dp))
                    }
                }
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Header(folderLabel(nodes, folder), stack.dropLast(1).lastOrNull() ?: start, nodes, stack.isNotEmpty(), onUp)
                }
                if (items.isEmpty()) {
                    item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            stringResource(R.string.folder_empty),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                }
                items(items, key = { it.id }) { tile -> TileCell(tile, inPrivateTab, onOpenUrl, onOpenFolder) { run(tile, it) } }
            }
        }
    }

    renaming?.let { id ->
        FolderNameDialog(
            stringResource(R.string.folder_rename_title), null, folderLabel(nodes, id), stringResource(R.string.action_save),
            onConfirm = { name -> renaming = null; scope.launch { library.renameFolder(id, name) } },
            onDismiss = { renaming = null },
        )
    }
    confirmDelete?.let { id ->
        DeleteFolderDialog(
            folderLabel(nodes, id), bookmarkCount(nodes, id),
            onConfirm = { confirmDelete = null; scope.launch { deleteWithUndo(library, id, snackbar, folderDeleted, undo) } },
            onDismiss = { confirmDelete = null },
        )
    }
}

@Composable
private fun Header(name: String, parent: String, nodes: List<BookmarkNodeEntity>, inSubfolder: Boolean, onUp: () -> Unit) {
    val style = MaterialTheme.typography.titleMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    if (inSubfolder) {
        Row(Modifier.padding(top = 16.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back_to_folder, folderLabel(nodes, parent)), tint = color)
            }
            Text(name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
        }
    } else {
        Text(name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp).semantics { heading() })
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileCell(tile: Tile, inPrivateTab: Boolean, onOpenUrl: (String) -> Unit, onOpenFolder: (String) -> Unit, onAction: (TileAction) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val label = when (tile) {
        is Tile.Bookmark -> tile.title.ifBlank { displayUrl(tile.url).text }
        is Tile.Folder -> tile.name
    }
    val folderDescription = (tile as? Tile.Folder)?.let {
        stringResource(R.string.cd_folder_tile, it.name, pluralStringResource(R.plurals.bookmarks_count, it.count, it.count))
    }
    Box(Modifier.fillMaxWidth().heightIn(min = 94.dp), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.width(80.dp).clip(RoundedCornerShape(12.dp))
                .combinedClickable(onLongClickLabel = stringResource(R.string.action_more), onLongClick = { menu = true }) {
                    when (tile) {
                        is Tile.Bookmark -> onOpenUrl(tile.url)
                        is Tile.Folder -> onOpenFolder(tile.id)
                    }
                }
                .padding(top = 8.dp, bottom = 2.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (tile) {
                is Tile.Bookmark -> SiteIconTile(tile.url, label, 56.dp, 16.dp, backfill = !inPrivateTab, letterStyle = MaterialTheme.typography.titleLarge)
                is Tile.Folder -> Box(
                    Modifier.size(56.dp).background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.onTertiaryContainer) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                    .then(if (folderDescription != null) Modifier.semantics { contentDescription = folderDescription } else Modifier),
            )
        }
        LumiDropdownMenu(menu, { menu = false }) {
            tileMenu(tile, inPrivateTab).forEach { action ->
                val (text, icon) = when (action) {
                    TileAction.OpenInNewTab -> R.string.link_open_new_tab to R.drawable.ic_open_in_new
                    TileAction.OpenInNewPrivateTab -> R.string.link_open_new_private_tab to R.drawable.ic_open_in_new
                    TileAction.OpenInPrivateTab -> R.string.link_open_private_tab to R.drawable.ic_domino_mask
                    TileAction.OpenFolder -> R.string.action_open to R.drawable.ic_folder
                    TileAction.Edit, TileAction.Rename -> (if (action == TileAction.Edit) R.string.action_edit else R.string.action_rename) to null
                    TileAction.DeleteBookmark, TileAction.DeleteFolder -> R.string.action_delete to null
                }
                MenuItem(stringResource(text), {
                    when {
                        icon != null -> Icon(painterResource(icon), null)
                        action == TileAction.Edit || action == TileAction.Rename -> Icon(Icons.Filled.Edit, null)
                        else -> Icon(Icons.Filled.Delete, null)
                    }
                }) { menu = false; onAction(action) }
            }
        }
    }
}
