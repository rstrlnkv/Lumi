package ru.digitalpunk.lumi.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.ui.theme.LocalDarkTheme
import ru.digitalpunk.lumi.ui.LocalSiteIcons
import ru.digitalpunk.lumi.address.resolveInput
import ru.digitalpunk.lumi.data.library.BookmarkNodeEntity
import ru.digitalpunk.lumi.data.library.ClearRange
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import ru.digitalpunk.lumi.data.library.LibraryRepository
import ru.digitalpunk.lumi.data.library.flatten
import ru.digitalpunk.lumi.engine.normalizeGroupName
import ru.digitalpunk.lumi.engine.sanitizeGroupInput
import ru.digitalpunk.lumi.library.folderName

/** The shown name of folder [id]; the root is "Bookmarks". */
@Composable
fun folderLabel(nodes: List<BookmarkNodeEntity>, id: String): String =
    if (id == ROOT) stringResource(R.string.bookmarks_root)
    else nodes.firstOrNull { it.id == id }?.let { folderName(it, stringResource(R.string.folder_favorites)) } ?: stringResource(R.string.bookmarks_root)

/** New folder or Rename: the name follows the group-name rules (no invisible-only names, a code-point cap). */
@Composable
fun FolderNameDialog(title: String, parentName: String?, initial: String, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val name = normalizeGroupName(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                parentName?.let { Text(stringResource(R.string.folder_in, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = sanitizeGroupInput(it) },
                    label = { Text(stringResource(R.string.folder_name)) },
                    singleLine = true,
                    modifier = Modifier.focusRequester(focus),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name) }, enabled = name.isNotEmpty()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * The folder tree as an indented list; a tap picks a row and closes. "New folder" creates one inside [selected] and picks it.
 * Without [includeRoot] the root row is left out and the folders move one level left.
 */
@Composable
fun FolderPickerDialog(
    library: LibraryRepository,
    nodes: List<BookmarkNodeEntity>,
    title: String,
    selected: String,
    disabled: Set<String>,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
    includeRoot: Boolean = true,
) {
    val dropped = if (includeRoot) 0 else 1
    var creating by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val favorites = stringResource(R.string.folder_favorites)
    val root = stringResource(R.string.bookmarks_root)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(flatten(nodes).drop(dropped), key = { it.id }) { row ->
                        val isSelected = row.id == selected
                        val enabled = row.id !in disabled
                        Row(
                            Modifier.fillMaxWidth().height(48.dp)
                                .background(if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(24.dp))
                                .selectable(isSelected, enabled, Role.RadioButton) { onPick(row.id) }
                                .alpha(if (enabled) 1f else 0.38f)
                                .padding(start = (24 * (row.depth - dropped)).dp, end = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val icon = when {
                                row.node == null -> R.drawable.ic_bookmarks
                                row.id == FAVORITES -> R.drawable.ic_folder_special
                                else -> R.drawable.ic_folder
                            }
                            Icon(painterResource(icon), null)
                            Spacer(Modifier.width(16.dp))
                            Text(
                                row.node?.let { folderName(it, favorites) } ?: root,
                                fontWeight = if (isSelected) FontWeight.Medium else null,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            if (isSelected) Icon(Icons.Filled.Check, null)
                        }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    Modifier.fillMaxWidth().height(48.dp).clickable(role = Role.Button) { creating = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_create_new_folder), null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(16.dp))
                    Text(stringResource(R.string.folder_new), color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
    if (creating) {
        FolderNameDialog(
            title = stringResource(R.string.folder_new),
            parentName = folderLabel(nodes, selected),
            initial = "",
            confirmLabel = stringResource(R.string.action_create),
            onConfirm = { name ->
                creating = false
                scope.launch { library.createFolder(selected, name)?.let(onPick) }
            },
            onDismiss = { creating = false },
        )
    }
}

@Composable
fun DeleteFolderDialog(name: String, bookmarks: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.delete_folder_title, name)) },
        text = { Text(pluralStringResource(R.plurals.delete_folder_body, bookmarks, bookmarks)) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(R.string.action_delete))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** The dialog is the confirmation: Clear acts at once. */
@Composable
fun ClearHistoryDialog(onClear: (ClearRange) -> Unit, onDismiss: () -> Unit) {
    var range by rememberSaveable { mutableStateOf(ClearRange.LastHour) }
    val labels = listOf(
        ClearRange.LastHour to R.string.history_clear_last_hour,
        ClearRange.Today to R.string.history_clear_today,
        ClearRange.AllTime to R.string.history_clear_all,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.history_clear_title)) },
        text = {
            Column {
                Column(Modifier.selectableGroup()) {
                    labels.forEach { (value, label) ->
                        Row(
                            Modifier.fillMaxWidth().height(48.dp).selectable(range == value, role = Role.RadioButton) { range = value },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = range == value, onClick = null)
                            Spacer(Modifier.width(16.dp))
                            Text(stringResource(label))
                        }
                    }
                }
                Text(stringResource(R.string.history_clear_note), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        },
        confirmButton = { TextButton(onClick = { onClear(range) }) { Text(stringResource(R.string.action_clear)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/**
 * Full-screen editor of bookmark [id]: title, URL (address-bar rules) and folder. Closes by itself if the bookmark is gone.
 * Saved from a private tab, it fetches no icon (the icon store is on disk and would record the site).
 */
@Composable
fun BookmarkEditorDialog(library: LibraryRepository, nodes: List<BookmarkNodeEntity>, id: String, inPrivateTab: Boolean, onDelete: () -> Unit, onDismiss: () -> Unit) {
    val icons = LocalSiteIcons.current
    val node = nodes.firstOrNull { it.id == id && !it.isFolder }
    if (node == null) {
        LaunchedEffect(Unit) { onDismiss() }
        return
    }
    var title by rememberSaveable(id) { mutableStateOf(node.title) }
    var url by rememberSaveable(id) { mutableStateOf(node.url.orEmpty()) }
    var folder by rememberSaveable(id) { mutableStateOf(node.parentId) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val resolved = resolveInput(url)
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        val lightBars = !LocalDarkTheme.current
        SideEffect {
            window?.let { WindowCompat.getInsetsController(it, it.decorView).run { isAppearanceLightStatusBars = lightBars; isAppearanceLightNavigationBars = lightBars } }
        }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, stringResource(R.string.cd_close)) }
                    Text(
                        stringResource(R.string.bookmark_editor_title),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
                    )
                    TextButton(
                        enabled = resolved != null,
                        onClick = {
                            scope.launch {
                                if (resolved != null && library.updateBookmark(id, title, resolved, folder)) {
                                    if (!inPrivateTab) icons?.request(resolved)
                                    onDismiss()
                                }
                            }
                        },
                    ) { Text(stringResource(R.string.action_save)) }
                }
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text(stringResource(R.string.bookmark_field_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(
                        url, { url = it },
                        label = { Text(stringResource(R.string.bookmark_field_url)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    val folderText = folderLabel(nodes, folder)
                    Box {
                        OutlinedTextField(
                            folderText, {},
                            readOnly = true,
                            label = { Text(stringResource(R.string.bookmark_field_folder)) },
                            leadingIcon = { Icon(painterResource(R.drawable.ic_folder), null) },
                            trailingIcon = { Icon(Icons.Filled.ArrowDropDown, null) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        val folderLabelText = stringResource(R.string.bookmark_field_folder)
                        Box(Modifier.matchParentSize().clickable(role = Role.Button, onClickLabel = folderLabelText) { picking = true })
                    }
                    TextButton(onClick = onDelete, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                        Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.bookmark_delete))
                    }
                }
            }
        }
        if (picking) {
            FolderPickerDialog(
                library, nodes, stringResource(R.string.folder_picker_title), folder, emptySet(),
                onPick = {
                    folder = it
                    picking = false
                },
                onDismiss = { picking = false },
            )
        }
    }
}
