package ru.digitalpunk.lumi.library

import ru.digitalpunk.lumi.data.library.BookmarkNodeEntity
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import ru.digitalpunk.lumi.data.library.canMoveNode
import ru.digitalpunk.lumi.data.library.flatten
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What Back does in the Library. */
sealed interface LibraryBack {
    data object CloseSearch : LibraryBack
    data class Up(val folder: String) : LibraryBack
    data object Close : LibraryBack
}

/** Search closes first, then a folder goes up one level (to the root if its parent is gone), then the Library closes. */
fun backTarget(folderId: String, searching: Boolean, nodes: List<BookmarkNodeEntity>): LibraryBack = when {
    searching -> LibraryBack.CloseSearch
    folderId == ROOT -> LibraryBack.Close
    else -> LibraryBack.Up(nodes.firstOrNull { it.id == folderId && it.isFolder }?.parentId?.takeIf { p -> p == ROOT || nodes.any { it.id == p && it.isFolder } } ?: ROOT)
}

/** The picker rows a move of [id] cannot go to: for a folder, itself and its subtree. */
fun disabledMoveTargets(nodes: List<BookmarkNodeEntity>, id: String): Set<String> =
    flatten(nodes).map { it.id }.filterNotTo(HashSet()) { canMoveNode(nodes, id, it) }

/** A never-renamed Favorites shows [favoritesDefault] in the current language. */
fun folderName(node: BookmarkNodeEntity, favoritesDefault: String): String =
    if (node.id == FAVORITES && node.title.isEmpty()) favoritesDefault else node.title

/** Only a web page that did not crash can be saved. */
fun canBookmark(url: String, crashed: Boolean): Boolean =
    !crashed && url.lowercase().let { it.startsWith("http://") || it.startsWith("https://") }

/** "Monday, 9 November" / «Понедельник, 9 ноября». */
fun weekdayDate(date: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern("EEEE, d MMMM", locale).format(date).replaceFirstChar { it.titlecase(locale) }

private val WEB_SCHEME = Regex("^https?://", RegexOption.IGNORE_CASE)

fun withoutScheme(url: String): String = url.replaceFirst(WEB_SCHEME, "")
