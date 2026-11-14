package ru.digitalpunk.lumi.ui.startpage

import android.content.res.Configuration
import android.os.SystemClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.spring
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.VectorConverter
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.zIndex
import androidx.compose.foundation.gestures.detectTapGestures
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.address.displayUrl
import ru.digitalpunk.lumi.data.library.BookmarkNodeEntity
import ru.digitalpunk.lumi.data.library.LibraryRepository
import ru.digitalpunk.lumi.data.library.bookmarkCount
import ru.digitalpunk.lumi.data.library.canMoveNode
import ru.digitalpunk.lumi.data.library.moveToIndex
import ru.digitalpunk.lumi.startpage.DragTarget
import ru.digitalpunk.lumi.startpage.FOLDER_DWELL_MILLIS
import ru.digitalpunk.lumi.startpage.Press
import ru.digitalpunk.lumi.startpage.Tile
import ru.digitalpunk.lumi.startpage.TileSlot
import ru.digitalpunk.lumi.startpage.TileAction
import ru.digitalpunk.lumi.startpage.currentFolder
import ru.digitalpunk.lumi.startpage.deleteAction
import ru.digitalpunk.lumi.startpage.dragTarget
import ru.digitalpunk.lumi.startpage.edgeScroll
import ru.digitalpunk.lumi.startpage.folderHighlighted
import ru.digitalpunk.lumi.startpage.gridColumns
import ru.digitalpunk.lumi.startpage.pressOutcome
import ru.digitalpunk.lumi.startpage.reconcileOrder
import ru.digitalpunk.lumi.startpage.shiftedIndex
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
    onBackgroundTap: (() -> Unit)? = null,
) {
    val folder = currentFolder(stack, start)
    // The default-browser card sits above the start folder only, never inside a sub-folder.
    val card = showDefaultCard && stack.isEmpty()
    val items = tiles(nodes, folder, stringResource(R.string.folder_favorites))
    val ids = items.map { it.id }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var renaming by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf(false) }
    var menuFor by remember { mutableStateOf<String?>(null) }
    val drag = remember { TileDrag() }
    val animScope = rememberCoroutineScope()
    // The lifted tile: 0..1 for its shadow and fill, its scale, and the leftover offset it settles from after the drop.
    val lift = remember { Animatable(0f) }
    val liftScale = remember { Animatable(1f) }
    val settle = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var settleId by remember { mutableStateOf<String?>(null) }
    val haptic = LocalHapticFeedback.current
    val grid = rememberLazyGridState()
    val shown = drag.order?.let { order -> val byId = items.associateBy { it.id }; reconcileOrder(order, ids).map(byId::getValue) } ?: items
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
            TileAction.EditOrder -> editing = true
            TileAction.OpenFolder -> onOpenFolder(tile.id)
            TileAction.Rename -> renaming = tile.id
            TileAction.DeleteFolder -> if ((tile as Tile.Folder).count > 0) confirmDelete = tile.id
                else scope.launch { deleteWithUndo(library, tile.id, snackbar, folderDeleted, undo) }
            TileAction.DeleteBookmark -> scope.launch { deleteWithUndo(library, tile.id, snackbar, bookmarkDeleted, undo) }
        }
    }

    // Over the focused bar a tap on the empty area ends editing; each tile cell takes the taps on its whole cell first.
    // While the tiles are in edit mode the tap ends that instead.
    val backgroundTap by rememberUpdatedState(onBackgroundTap)
    val background = Modifier.pointerInput(Unit) { detectTapGestures { if (editing) editing = false else backgroundTap?.invoke() } }
    if (editing) BackHandler { editing = false }

    val lead = (if (card) 1 else 0) + 1
    val slots = { dragged: String ->
        val ours = ids.toSet()
        grid.layoutInfo.visibleItemsInfo.mapNotNull { info ->
            val id = info.key as? String ?: return@mapNotNull null
            if (id !in ours) return@mapNotNull null
            val receives = items.firstOrNull { it.id == id } is Tile.Folder && canMoveNode(nodes, dragged, id)
            TileSlot(id, info.index - lead, info.offset.x.toFloat(), info.offset.y.toFloat(), info.size.width.toFloat(), info.size.height.toFloat(), receives)
        }
    }
    val retarget = rememberUpdatedState<() -> Unit> {
        val id = drag.id
        if (id != null) when (val target = dragTarget(slots(id), id, drag.finger.x, drag.finger.y)) {
            is DragTarget.Move -> { drag.order = moveToIndex(drag.order ?: ids, id, target.index); drag.overFolder = null; drag.overSince = null }
            is DragTarget.Into -> if (target.folder != drag.overFolder) { drag.overFolder = target.folder; drag.overSince = SystemClock.uptimeMillis() }
            DragTarget.Stay -> { drag.overFolder = null; drag.overSince = null }
        }
    }
    // The order is written once, on release; the shown order stays until the stored one arrives in [nodes].
    val finish = rememberUpdatedState<(Boolean) -> Unit> { drop ->
        val id = drag.id
        if (id != null) {
            val order = drag.order
            val into = drag.overFolder?.takeIf { folderHighlighted(drag.overSince, SystemClock.uptimeMillis()) }
            val at = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == id }?.offset
            val residual = if (at != null) Offset(drag.finger.x - drag.grab.x - at.x, drag.finger.y - drag.grab.y - at.y) else Offset.Zero
            drag.id = null; drag.overFolder = null; drag.overSince = null
            settleId = id
            animScope.launch {
                settle.snapTo(residual)
                settle.animateTo(Offset.Zero, spring(stiffness = 400f))
                if (settleId == id) settleId = null
            }
            if (drop) haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            // A write that changes nothing (false) leaves no stored order to wait for, so the shown order goes back at once.
            val written: suspend (Boolean) -> Unit = { ok -> if (!ok) { drag.order = null; drag.released = false } }
            when {
                !drop || (into == null && (order == null || order == ids)) -> drag.order = null
                into != null -> { drag.released = true; scope.launch { written(library.moveNode(id, into)) } }
                else -> { drag.released = true; scope.launch { written(library.reorder(id, order!!.indexOf(id))) } }
            }
        }
    }
    LaunchedEffect(nodes) { if (drag.released) { drag.order = null; drag.released = false } }
    val density = LocalDensity.current
    LaunchedEffect(drag.id) {
        while (drag.id != null) {
            withFrameNanos {}
            val bottom = (grid.layoutInfo.viewportEndOffset - grid.layoutInfo.afterContentPadding).toFloat()
            val step = edgeScroll(drag.finger.y, 0f, bottom, with(density) { 72.dp.toPx() }, with(density) { 16.dp.toPx() })
            if (step != 0f) { grid.scrollBy(step); retarget.value() }
        }
    }
    var dwellEnd by remember { mutableLongStateOf(0L) }
    LaunchedEffect(drag.overFolder) {
        if (drag.overFolder != null) { delay(FOLDER_DWELL_MILLIS); dwellEnd = SystemClock.uptimeMillis() }
    }
    val highlighted = drag.overFolder?.takeIf { folderHighlighted(drag.overSince, dwellEnd) }
    val resolver = LocalContext.current.contentResolver
    val reduceMotion = remember(editing) { Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    LaunchedEffect(drag.id != null) { lift.animateTo(if (drag.id != null) 1f else 0f, tween(150)) }
    LaunchedEffect(drag.id != null, highlighted != null) {
        // Over a folder the dragged tile shrinks so the folder's highlight shows; the tick marks the highlight turning on.
        if (highlighted != null) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
        liftScale.animateTo(if (drag.id == null) 1f else if (highlighted != null) 0.7f else 1.08f, spring(stiffness = 600f))
    }
    val moveLeftLabel = stringResource(R.string.action_move_left)
    val moveRightLabel = stringResource(R.string.action_move_right)
    val deleteLabel = stringResource(R.string.action_delete)

    Box(modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).then(background)) {
        // An empty start folder is a plain page: the address field has the focus.
        if (stack.isNotEmpty() || items.isNotEmpty() || card) {
            LazyVerticalGrid(
                GridCells.Fixed(gridColumns(landscape)),
                Modifier.fillMaxSize().pointerInput(editing) {
                    // On the Initial pass nothing is consumed until a drag starts: a scroll, a tap or a long press go on as before.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val tile = slots("").firstOrNull { down.position.x >= it.left && down.position.x < it.left + it.width && down.position.y >= it.top && down.position.y < it.top + it.height }
                            ?: return@awaitEachGesture
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: return@awaitEachGesture
                            if (!change.pressed) return@awaitEachGesture
                            val moved = (change.position - down.position).getDistance()
                            when (pressOutcome(editing, change.uptimeMillis - down.uptimeMillis, moved, viewConfiguration.longPressTimeoutMillis, viewConfiguration.touchSlop)) {
                                Press.Wait -> continue
                                Press.Scroll -> return@awaitEachGesture
                                Press.Drag -> break
                            }
                        }
                        menuFor = null
                        drag.id = tile.id
                        drag.finger = down.position
                        drag.grab = down.position - Offset(tile.left, tile.top)
                        drag.order = ids
                        settleId = null
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        try {
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) { finish.value(true); break }
                                drag.finger = change.position
                                retarget.value()
                            }
                        } finally {
                            finish.value(false)
                        }
                    }
                },
                state = grid,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = bottomInset),
            ) {
                if (card) {
                    item(key = "default_card", span = { GridItemSpan(maxLineSpan) }) {
                        DefaultBrowserCard(onDefaultNotNow, onDefaultSet, Modifier.padding(top = 16.dp))
                    }
                }
                item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
                    Header(folderLabel(nodes, folder), stack.dropLast(1).lastOrNull() ?: start, nodes, stack.isNotEmpty(), onUp, if (editing) ({ editing = false }) else null)
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
                itemsIndexed(shown, key = { _, tile -> tile.id }) { index, tile ->
                    val dragging = drag.id == tile.id
                    val lifted = dragging || settleId == tile.id
                    val cellModifier = if (lifted) Modifier.zIndex(1f).graphicsLayer {
                        if (dragging) {
                            val at = grid.layoutInfo.visibleItemsInfo.firstOrNull { it.key == tile.id }?.offset
                            if (at != null) { translationX = drag.finger.x - drag.grab.x - at.x; translationY = drag.finger.y - drag.grab.y - at.y }
                        } else { translationX = settle.value.x; translationY = settle.value.y }
                        scaleX = liftScale.value; scaleY = liftScale.value
                    } else Modifier.animateItem()
                    val wobble = if (editing && !reduceMotion && !lifted) wobbleAngle(index * 47 % 160) else null
                    val earlier = shiftedIndex(index, shown.size, -1)
                    val later = shiftedIndex(index, shown.size, 1)
                    TileCell(
                        tile, inPrivateTab, editing, menuFor == tile.id, highlighted == tile.id, wobble, if (index % 2 == 0) 1f else -1f,
                        moveLeftLabel, moveRightLabel, deleteLabel, drag.id != null, if (lifted) ({ lift.value }) else null,
                        earlier?.let { { scope.launch { library.reorder(tile.id, it) } } },
                        later?.let { { scope.launch { library.reorder(tile.id, it) } } },
                        onOpenUrl, onOpenFolder, { menuFor = if (it) tile.id else null }, cellModifier,
                    ) { run(tile, it) }
                }
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
private fun Header(name: String, parent: String, nodes: List<BookmarkNodeEntity>, inSubfolder: Boolean, onUp: () -> Unit, onDone: (() -> Unit)?) {
    val style = MaterialTheme.typography.titleMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth().padding(top = if (inSubfolder) 16.dp else 12.dp, bottom = 8.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        if (inSubfolder) {
            IconButton(onClick = onUp) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.cd_back_to_folder, folderLabel(nodes, parent)), tint = color)
            }
        }
        Text(name, style = style, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() })
        if (onDone != null) TextButton(onDone) { Text(stringResource(R.string.action_done)) }
    }
}

/** The drag of one tile: where the finger is, the order shown meanwhile, and the folder it hovers over. */
private class TileDrag {
    var id by mutableStateOf<String?>(null)
    var finger by mutableStateOf(Offset.Zero)
    var grab = Offset.Zero
    var order by mutableStateOf<List<String>?>(null)
    var overFolder by mutableStateOf<String?>(null)
    var overSince: Long? = null
    var released = false
}

@Composable
private fun wobbleAngle(phaseMillis: Int): State<Float> =
    rememberInfiniteTransition(label = "wobble").animateFloat(
        -1.2f, 1.2f,
        infiniteRepeatable(tween(160, easing = FastOutSlowInEasing), RepeatMode.Reverse, StartOffset(phaseMillis, StartOffsetType.FastForward)),
        label = "wobble",
    )

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TileCell(
    tile: Tile,
    inPrivateTab: Boolean,
    editing: Boolean,
    menuOpen: Boolean,
    highlighted: Boolean,
    wobble: State<Float>?,
    wobbleSign: Float,
    moveLeftLabel: String,
    moveRightLabel: String,
    deleteLabel: String,
    dragActive: Boolean,
    lift: (() -> Float)?,
    moveEarlier: (() -> Unit)?,
    moveLater: (() -> Unit)?,
    onOpenUrl: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onMenu: (Boolean) -> Unit,
    modifier: Modifier,
    onAction: (TileAction) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val label = when (tile) {
        is Tile.Bookmark -> tile.title.ifBlank { displayUrl(tile.url).text }
        is Tile.Folder -> tile.name
    }
    val folderDescription = (tile as? Tile.Folder)?.let {
        stringResource(R.string.cd_folder_tile, it.name, pluralStringResource(R.plurals.bookmarks_count, it.count, it.count))
    }
    val delete = deleteAction(tile)
    // The cell swallows taps beside the tile, so a near miss in the gap between tiles does not end editing.
    BoxWithConstraints(
        modifier.fillMaxWidth().heightIn(min = 102.dp)
            .graphicsLayer { rotationZ = (wobble?.value ?: 0f) * wobbleSign }
            .pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.TopCenter,
    ) {
        // The badge is a sibling of the tile, not its child, so its touch box that overhangs the tile's corner is not clipped by the tile's bounds.
        Box(
            Modifier.padding(top = 16.dp).width(80.dp).graphicsLayer {
                val raised = lift?.invoke() ?: 0f
                shadowElevation = 8.dp.toPx() * raised
                shape = RoundedCornerShape(12.dp)
                clip = false
            },
        ) {
            val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHigh
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .drawBehind { drawRoundRect(surfaceHigh.copy(alpha = lift?.invoke() ?: 0f), cornerRadius = CornerRadius(12.dp.toPx())) }
                    .then(
                        if (editing) Modifier.semantics(mergeDescendants = true) {
                            customActions = listOfNotNull(
                                moveEarlier?.let { CustomAccessibilityAction(moveLeftLabel) { it(); true } },
                                moveLater?.let { CustomAccessibilityAction(moveRightLabel) { it(); true } },
                                delete?.let { CustomAccessibilityAction(deleteLabel) { onAction(it); true } },
                            )
                        } else Modifier.combinedClickable(
                            onLongClickLabel = stringResource(R.string.action_more),
                            onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onMenu(true) },
                        ) {
                            when (tile) {
                                is Tile.Bookmark -> onOpenUrl(tile.url)
                                is Tile.Folder -> onOpenFolder(tile.id)
                            }
                        },
                    )
                    .padding(top = 8.dp, bottom = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (tile) {
                    is Tile.Bookmark -> SiteIconTile(tile.url, label, 64.dp, 16.dp, backfill = !inPrivateTab, letterStyle = MaterialTheme.typography.titleLarge, smallAsLetter = true)
                    is Tile.Folder -> {
                        val primaryContainer = MaterialTheme.colorScheme.primaryContainer
                        val fill by animateColorAsState(if (highlighted) primaryContainer else primaryContainer.copy(alpha = 0f), tween(100), label = "folderFill")
                        val ring by animateColorAsState(MaterialTheme.colorScheme.primary.copy(alpha = if (highlighted) 1f else 0f), tween(100), label = "folderRing")
                        val tint by animateColorAsState(
                            if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer, tween(100), label = "folderIcon",
                        )
                        Box(
                            Modifier.size(64.dp).background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(16.dp))
                                .background(fill, RoundedCornerShape(16.dp))
                                .border(2.dp, ring, RoundedCornerShape(16.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(painterResource(R.drawable.ic_folder), null, Modifier.size(32.dp), tint = tint)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)
                        .then(if (folderDescription != null) Modifier.semantics { contentDescription = folderDescription } else Modifier),
                )
            }
        }
        if (editing && delete != null && !dragActive) {
            // Centred on the icon's top-start corner (32 dp left of the cell centre): the touch area is 48 dp, the disc 24 dp with a 2 dp ring;
            // the delete action also sits in the cell's TalkBack actions. On a cell too narrow to hold the 48 dp box, the disc moves inward.
            Box(
                Modifier.align(Alignment.TopCenter).offset(x = -minOf(32.dp, maxWidth / 2 - 24.dp)).size(48.dp).clip(CircleShape).clickable { onAction(delete) }.clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.surface, CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(24.dp).background(MaterialTheme.colorScheme.error, CircleShape), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(12.dp, 2.dp).background(MaterialTheme.colorScheme.onError))
                    }
                }
            }
        }
        LumiDropdownMenu(menuOpen, { onMenu(false) }) {
            tileMenu(tile, inPrivateTab).forEach { action ->
                val (text, icon) = when (action) {
                    TileAction.OpenInNewTab -> R.string.link_open_new_tab to R.drawable.ic_open_in_new
                    TileAction.OpenInNewPrivateTab -> R.string.link_open_new_private_tab to R.drawable.ic_open_in_new
                    TileAction.OpenInPrivateTab -> R.string.link_open_private_tab to R.drawable.ic_domino_mask
                    TileAction.OpenFolder -> R.string.action_open to R.drawable.ic_folder
                    TileAction.EditOrder -> R.string.action_edit_order to R.drawable.ic_grid_view
                    TileAction.Edit, TileAction.Rename -> (if (action == TileAction.Edit) R.string.action_edit else R.string.action_rename) to null
                    TileAction.DeleteBookmark, TileAction.DeleteFolder -> R.string.action_delete to null
                }
                MenuItem(stringResource(text), {
                    when {
                        icon != null -> Icon(painterResource(icon), null)
                        action == TileAction.Edit || action == TileAction.Rename -> Icon(Icons.Filled.Edit, null)
                        else -> Icon(Icons.Filled.Delete, null)
                    }
                }) { onMenu(false); onAction(action) }
            }
        }
    }
}
