package ru.digitalpunk.lumi.startpage

import ru.digitalpunk.lumi.data.library.BookmarkNodeEntity
import ru.digitalpunk.lumi.data.library.bookmarkCount
import ru.digitalpunk.lumi.data.library.canDelete
import ru.digitalpunk.lumi.data.library.children
import ru.digitalpunk.lumi.library.folderName

/** The start page covers a blank page once its own load is over, unless the page crashed. */
fun startPageVisible(url: String, loading: Boolean, crashed: Boolean): Boolean =
    url.equals("about:blank", ignoreCase = true) && !loading && !crashed

/** A new tab focuses the address field only when the start page has nothing to show. */
fun focusNewTab(startItems: Int): Boolean = startItems == 0

/** The open folder: the last one in [stack], or [start] at the top. */
fun currentFolder(stack: List<String>, start: String): String = stack.lastOrNull() ?: start

fun navInto(stack: List<String>, id: String): List<String> = stack + id

fun navUp(stack: List<String>): List<String> = stack.dropLast(1)

/** The longest prefix of [stack] that is still a chain of folders down from [start]: a deleted, moved or re-rooted folder goes. */
fun reconcile(stack: List<String>, nodes: List<BookmarkNodeEntity>, start: String): List<String> {
    var parent = start
    return stack.takeWhile { id ->
        val ok = nodes.any { it.id == id && it.isFolder && it.parentId == parent }
        if (ok) parent = id
        ok
    }
}

/** Back climbs one folder on the start page; the address field and anything covering the page take Back first. */
fun backGoesUp(visible: Boolean, stack: List<String>, editing: Boolean, covered: Boolean): Boolean =
    visible && stack.isNotEmpty() && !editing && !covered

enum class EditLayer { None, StartPage, Suggestions }

/** What fills the area above the focused bar. [initial] is the URL text the edit started with. */
fun editLayer(editing: Boolean, text: String, initial: String, startItems: Int): EditLayer = when {
    !editing -> EditLayer.None
    startItems > 0 && (text.isBlank() || text == initial) -> EditLayer.StartPage
    // Typed, or nothing to show: the scrim and the list, as before.
    else -> EditLayer.Suggestions
}

fun gridColumns(landscape: Boolean): Int = if (landscape) 8 else 4

sealed interface Tile {
    val id: String

    data class Bookmark(override val id: String, val title: String, val url: String) : Tile
    data class Folder(override val id: String, val name: String, val count: Int) : Tile
}

/** The tiles of [folder] in its own order; a folder tile counts every bookmark below it. */
fun tiles(nodes: List<BookmarkNodeEntity>, folder: String, favoritesDefault: String): List<Tile> =
    children(nodes, folder).map { node ->
        if (node.isFolder) Tile.Folder(node.id, folderName(node, favoritesDefault), bookmarkCount(nodes, node.id))
        else Tile.Bookmark(node.id, node.title, node.url.orEmpty())
    }

enum class TileAction { OpenInNewTab, OpenInNewPrivateTab, OpenInPrivateTab, Edit, EditOrder, DeleteBookmark, OpenFolder, Rename, DeleteFolder }

/** The long-press menu. A private tab opens bookmarks only in new private tabs; Favorites cannot be deleted. */
fun tileMenu(tile: Tile, inPrivateTab: Boolean): List<TileAction> = when (tile) {
    is Tile.Bookmark ->
        if (inPrivateTab) listOf(TileAction.OpenInNewPrivateTab, TileAction.Edit, TileAction.EditOrder, TileAction.DeleteBookmark)
        else listOf(TileAction.OpenInNewTab, TileAction.OpenInPrivateTab, TileAction.Edit, TileAction.EditOrder, TileAction.DeleteBookmark)
    is Tile.Folder -> listOfNotNull(TileAction.OpenFolder, TileAction.Rename, TileAction.EditOrder, TileAction.DeleteFolder.takeIf { canDelete(tile.id) })
}

/** The delete action of [tile] in edit mode; null for Favorites. */
fun deleteAction(tile: Tile): TileAction? = when (tile) {
    is Tile.Bookmark -> TileAction.DeleteBookmark
    is Tile.Folder -> TileAction.DeleteFolder.takeIf { canDelete(tile.id) }
}

enum class Press { Wait, Scroll, Drag }

/**
 * What a finger held on a tile has become: it waits while it stays within [slopPx]; past the slop it drags
 * in edit mode or after [longPressMillis], otherwise it is a scroll.
 */
fun pressOutcome(editing: Boolean, heldMillis: Long, movedPx: Float, longPressMillis: Long, slopPx: Float): Press = when {
    movedPx <= slopPx -> Press.Wait
    editing || heldMillis >= longPressMillis -> Press.Drag
    else -> Press.Scroll
}

/** A tile's cell in the grid, in the grid's own pixels; [folder] is true when it may receive the dragged tile. */
data class TileSlot(val id: String, val index: Int, val left: Float, val top: Float, val width: Float, val height: Float, val folder: Boolean)

sealed interface DragTarget {
    data object Stay : DragTarget
    data class Move(val index: Int) : DragTarget
    data class Into(val folder: String) : DragTarget
}

/** The middle share of a folder cell (each way) that means "drop into"; the ring around it means "reorder here". */
private const val FOLDER_CORE = 0.6f

/** Where a drag at ([x], [y]) would drop: the core of a receiving folder is [DragTarget.Into], the core's start edge included and its end edge not. */
fun dragTarget(slots: List<TileSlot>, dragged: String, x: Float, y: Float): DragTarget {
    val slot = slots.firstOrNull { x >= it.left && x < it.left + it.width && y >= it.top && y < it.top + it.height } ?: return DragTarget.Stay
    if (slot.id == dragged) return DragTarget.Stay
    val margin = (1f - FOLDER_CORE) / 2f
    val core = x >= slot.left + slot.width * margin && x < slot.left + slot.width * (1f - margin) &&
        y >= slot.top + slot.height * margin && y < slot.top + slot.height * (1f - margin)
    return if (slot.folder && core) DragTarget.Into(slot.id) else DragTarget.Move(slot.index)
}

const val FOLDER_DWELL_MILLIS = 700L

/** A folder lights up once the finger has been over it since [overSince] for [FOLDER_DWELL_MILLIS]. */
fun folderHighlighted(overSince: Long?, now: Long): Boolean = overSince != null && now - overSince >= FOLDER_DWELL_MILLIS

/** The displayed [order] without ids that are gone, with new ids from [ids] at the end. */
fun reconcileOrder(order: List<String>, ids: List<String>): List<String> {
    val known = ids.toSet()
    val kept = order.filter { it in known }
    return kept + (ids - kept.toSet())
}

/** The scroll step for a finger at [y]: negative in the top zone, positive in the bottom zone, scaled by the depth, else 0. */
fun edgeScroll(y: Float, top: Float, bottom: Float, zone: Float, maxStep: Float): Float {
    val z = minOf(zone, (bottom - top) / 2f)
    if (z <= 0f) return 0f
    return when {
        y < top + z -> -maxStep * ((top + z - y) / z).coerceAtMost(1f)
        y > bottom - z -> maxStep * ((y - (bottom - z)) / z).coerceAtMost(1f)
        else -> 0f
    }
}

/** [index] moved by [by] places, or null when that leaves the [count] tiles. */
fun shiftedIndex(index: Int, count: Int, by: Int): Int? = (index + by).takeIf { it in 0 until count }
