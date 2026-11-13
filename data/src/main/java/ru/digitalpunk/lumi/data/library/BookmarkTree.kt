package ru.digitalpunk.lumi.data.library

import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import java.net.URI
import java.net.URISyntaxException

/** A folder row of the picker: [node] is null for the root. */
data class FolderRow(val id: String, val depth: Int, val node: BookmarkNodeEntity?)

private val nodeOrder = compareBy<BookmarkNodeEntity>({ it.position }, { it.createdAt }, { it.id })

/** Children by parent id. A node whose parent is not an existing folder (an orphan) is put under the root. */
private fun childMap(nodes: List<BookmarkNodeEntity>): Map<String, List<BookmarkNodeEntity>> {
    val folders = nodes.filter { it.isFolder }.mapTo(HashSet()) { it.id }
    return nodes.groupBy { if (it.parentId in folders) it.parentId else ROOT }.mapValues { (_, list) -> list.sortedWith(nodeOrder) }
}

fun children(nodes: List<BookmarkNodeEntity>, parent: String): List<BookmarkNodeEntity> = childMap(nodes)[parent].orEmpty()

/** Every node below [id] (not [id] itself), depth first. A cycle in a corrupt file stops at the first repeat. */
private fun descendants(nodes: List<BookmarkNodeEntity>, id: String): List<BookmarkNodeEntity> {
    val map = childMap(nodes)
    val seen = hashSetOf(id)
    val out = ArrayList<BookmarkNodeEntity>()
    fun walk(parent: String) {
        map[parent].orEmpty().forEach { if (seen.add(it.id)) { out += it; walk(it.id) } }
    }
    walk(id)
    return out
}

/** The root, then every folder reachable from it, depth first in order. Bookmarks are left out. */
fun flatten(nodes: List<BookmarkNodeEntity>): List<FolderRow> {
    val map = childMap(nodes)
    val seen = hashSetOf(ROOT)
    val out = mutableListOf(FolderRow(ROOT, 0, null))
    fun walk(parent: String, depth: Int) {
        map[parent].orEmpty().filter { it.isFolder }.forEach { if (seen.add(it.id)) { out += FolderRow(it.id, depth, it); walk(it.id, depth + 1) } }
    }
    walk(ROOT, 1)
    return out
}

fun descendantFolderIds(nodes: List<BookmarkNodeEntity>, id: String): Set<String> =
    descendants(nodes, id).filter { it.isFolder }.mapTo(HashSet()) { it.id }

/** [id] and everything below it; empty for an unknown id. */
fun subtree(nodes: List<BookmarkNodeEntity>, id: String): List<BookmarkNodeEntity> {
    val node = nodes.firstOrNull { it.id == id } ?: return emptyList()
    return listOf(node) + descendants(nodes, id)
}

fun bookmarkCount(nodes: List<BookmarkNodeEntity>, folder: String): Int = descendants(nodes, folder).count { !it.isFolder }

/** The start page's folder: [stored] while it is an existing folder other than the root, otherwise Favorites. */
fun effectiveStartFolder(nodes: List<BookmarkNodeEntity>, stored: String): String =
    if (stored != ROOT && nodes.any { it.id == stored && it.isFolder }) stored else FAVORITES

fun canDelete(id: String): Boolean = id != FAVORITES

/** Whether [id] may go into [newParent]: the root or an existing folder, never Favorites itself, never a folder into its own subtree. */
fun canMoveNode(nodes: List<BookmarkNodeEntity>, id: String, newParent: String): Boolean {
    val node = nodes.firstOrNull { it.id == id } ?: return false
    if (id == FAVORITES || id == newParent) return false
    if (newParent != ROOT && nodes.none { it.id == newParent && it.isFolder }) return false
    return !node.isFolder || newParent !in descendantFolderIds(nodes, id)
}

/** The newest bookmark for the same page as [url] (by [bookmarkUrlKey]), in any folder. */
fun findBookmarkByUrl(nodes: List<BookmarkNodeEntity>, url: String): BookmarkNodeEntity? {
    val key = bookmarkUrlKey(url)
    return nodes.filter { !it.isFolder && it.url != null && bookmarkUrlKey(it.url) == key }.maxByOrNull { it.createdAt }
}

/**
 * The page identity of [url]: scheme and host in lower case, no trailing dot on the host, no default port,
 * an empty path read as "/", the query kept and the fragment dropped. `www.` is kept: another host is another page.
 * A host that is not a strict DNS name (an underscore, non-ASCII letters) is read from the authority.
 * A string that does not parse is its own trimmed key.
 */
fun bookmarkUrlKey(url: String): String {
    val raw = url.trim()
    val uri = try { URI(raw) } catch (_: URISyntaxException) { return raw }
    val scheme = uri.scheme?.lowercase() ?: return raw
    val authority = uri.rawAuthority ?: return raw
    val user = if ('@' in authority) authority.substringBeforeLast('@') + "@" else ""
    val hostPort = authority.substringAfterLast('@')
    val portText = hostPort.substringAfterLast(':', "").takeIf { it.all(Char::isDigit) }
    val host = (if (portText == null) hostPort else hostPort.substringBeforeLast(':')).lowercase().removeSuffix(".")
    val port = portText?.toIntOrNull()?.takeIf { it != defaultPort(scheme) }?.let { ":$it" }.orEmpty()
    val path = uri.rawPath.orEmpty().ifEmpty { "/" }
    val query = uri.rawQuery?.let { "?$it" }.orEmpty()
    return "$scheme://$user$host$port$path$query"
}

private fun defaultPort(scheme: String) = when (scheme) {
    "http" -> 80
    "https" -> 443
    else -> -2
}
