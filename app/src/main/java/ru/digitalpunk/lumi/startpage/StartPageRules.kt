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

enum class TileAction { OpenInNewTab, OpenInNewPrivateTab, OpenInPrivateTab, Edit, DeleteBookmark, OpenFolder, Rename, DeleteFolder }

/** The long-press menu. A private tab opens bookmarks only in new private tabs; Favorites cannot be deleted. */
fun tileMenu(tile: Tile, inPrivateTab: Boolean): List<TileAction> = when (tile) {
    is Tile.Bookmark ->
        if (inPrivateTab) listOf(TileAction.OpenInNewPrivateTab, TileAction.Edit, TileAction.DeleteBookmark)
        else listOf(TileAction.OpenInNewTab, TileAction.OpenInPrivateTab, TileAction.Edit, TileAction.DeleteBookmark)
    is Tile.Folder -> listOfNotNull(TileAction.OpenFolder, TileAction.Rename, TileAction.DeleteFolder.takeIf { canDelete(tile.id) })
}
