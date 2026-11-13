package ru.digitalpunk.lumi.data.library

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.digitalpunk.lumi.data.icons.IconRetention
import ru.digitalpunk.lumi.data.icons.bookmarkIconHosts
import ru.digitalpunk.lumi.data.icons.orphanedIconHosts
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import ru.digitalpunk.lumi.data.tabs.TabDatabase
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

/**
 * Bookmarks and history in `tabs.db`. Writes are serialised; a failure is logged, and a call that returns a value
 * reports it as false or null. Titles are cut to [MAX_TITLE_LENGTH]; a bookmark URL over [MAX_URL_LENGTH] is refused.
 * A host's site icon goes with its last bookmark (delete or URL edit), and "All time" clears the icons of hosts with none.
 */
class LibraryRepository(
    private val dao: LibraryDao,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    private val icons: IconRetention? = null,
) {
    private val mutex = Mutex()

    val nodes: Flow<List<BookmarkNodeEntity>> = dao.observeNodes().catch { Log.e(TAG, "Reading bookmarks failed", it) }
    val history: Flow<List<HistoryEntity>> = dao.observeHistory(HISTORY_LIMIT).catch { Log.e(TAG, "Reading history failed", it) }

    /** Seeds Favorites if it is missing and prunes visits older than [HISTORY_KEEP_DAYS]. */
    suspend fun open() {
        write("Opening the library", Unit) {
            dao.insertIfAbsent(BookmarkNodeEntity(FAVORITES, ROOT, BookmarkNodeEntity.FOLDER, "", null, 0, 0))
            dao.pruneVisits(now() - HISTORY_KEEP_DAYS * DAY_MILLIS)
        }
    }

    /** The new bookmark's id, at the end of [parent]; null for an empty URL. A blank title becomes the host. */
    suspend fun addBookmark(parent: String, title: String, url: String): String? {
        if (url.isBlank() || url.length > MAX_URL_LENGTH) return null
        return write("Adding a bookmark", null) {
            val id = newId()
            dao.append(BookmarkNodeEntity(id, parent, BookmarkNodeEntity.BOOKMARK, titleOrHost(title, url), url, 0, now()))
            id
        }
    }

    /** A new folder in [parent], or null when [name] is empty (the caller cleans it). */
    suspend fun createFolder(parent: String, name: String): String? {
        if (name.isBlank()) return null
        return write("Creating a folder", null) {
            val id = newId()
            dao.append(BookmarkNodeEntity(id, parent, BookmarkNodeEntity.FOLDER, name.take(MAX_TITLE_LENGTH), null, 0, now()))
            id
        }
    }

    suspend fun renameFolder(id: String, name: String): Boolean {
        if (name.isBlank()) return false
        return write("Renaming a folder", false) {
            val node = dao.nodes().firstOrNull { it.id == id && it.isFolder } ?: return@write false
            dao.update(node.copy(title = name.take(MAX_TITLE_LENGTH)))
            true
        }
    }

    /** Changes title and URL, and moves the bookmark to the end of [parent] when that changed. */
    suspend fun updateBookmark(id: String, title: String, url: String, parent: String): Boolean {
        if (url.isBlank() || url.length > MAX_URL_LENGTH) return false
        return write("Updating a bookmark", false) {
            val all = dao.nodes()
            val node = all.firstOrNull { it.id == id && !it.isFolder } ?: return@write false
            val edited = node.copy(title = titleOrHost(title, url), url = url)
            when {
                parent == node.parentId -> dao.update(edited)
                canMoveNode(all, id, parent) -> dao.append(edited.copy(parentId = parent))
                else -> return@write false
            }
            forgetIcons(all.map { it.url }, all.map { if (it.id == id) url else it.url })
            true
        }
    }

    /** Moves [id] to the end of [newParent]; false for Favorites or a folder into its own subtree. */
    suspend fun moveNode(id: String, newParent: String): Boolean = write("Moving a bookmark", false) {
        val all = dao.nodes()
        if (!canMoveNode(all, id, newParent)) return@write false
        val node = all.first { it.id == id }
        if (node.parentId != newParent) dao.append(node.copy(parentId = newParent))
        true
    }

    /** The removed subtree, for Undo; null for Favorites, an unknown id or a failure. */
    suspend fun delete(id: String): List<BookmarkNodeEntity>? {
        if (!canDelete(id)) return null
        return write("Deleting a bookmark", null) {
            val all = dao.nodes()
            val removed = subtree(all, id).ifEmpty { return@write null }
            removed.map { it.id }.chunked(CHUNK).forEach { dao.deleteNodes(it) }
            val gone = removed.mapTo(HashSet()) { it.id }
            forgetIcons(all.map { it.url }, all.filterNot { it.id in gone }.map { it.url })
            removed
        }
    }

    /** Puts [removed] back with the same ids and positions; a node whose parent is gone goes to the root. */
    suspend fun restore(removed: List<BookmarkNodeEntity>) {
        write("Restoring bookmarks", Unit) {
            val folders = (dao.nodes() + removed).filter { it.isFolder }.mapTo(HashSet()) { it.id }
            dao.insertAll(removed.map { if (it.parentId == ROOT || it.parentId in folders) it else it.copy(parentId = ROOT) })
        }
    }

    suspend fun recordVisit(url: String, atMillis: Long) {
        write("Recording a visit", Unit) { dao.insertVisit(HistoryEntity(url = url, title = "", visitedAt = atMillis)) }
    }

    /** Names the newest visit of [url]. */
    suspend fun titleVisit(url: String, title: String) {
        write("Naming a visit", Unit) { dao.titleLatest(url, title.take(MAX_TITLE_LENGTH)) }
    }

    suspend fun deleteVisits(ids: List<Long>) {
        write("Deleting visits", Unit) { ids.chunked(CHUNK).forEach { dao.deleteVisits(it) } }
    }

    suspend fun restoreVisits(rows: List<HistoryEntity>) {
        write("Restoring visits", Unit) { dao.insertVisits(rows) }
    }

    suspend fun clearHistory(range: ClearRange, zone: ZoneId) {
        write("Clearing history", Unit) {
            val from = clearFrom(range, Instant.ofEpochMilli(now()), zone)
            if (from == null) {
                dao.deleteAllVisits()
                icons?.retainOnly(bookmarkIconHosts(dao.nodes().map { it.url }))
            } else {
                dao.deleteVisitsSince(from)
            }
        }
    }

    /** After the rows are written: a failure here is logged and never turns a saved change into a failed one. */
    private fun forgetIcons(before: List<String?>, after: List<String?>) {
        val hosts = orphanedIconHosts(before, after)
        if (hosts.isEmpty()) return
        try {
            icons?.forget(hosts)
        } catch (e: Exception) {
            Log.e(TAG, "Deleting site icons failed", e)
        }
    }

    private suspend fun <T> write(what: String, onFailure: T, block: suspend () -> T): T = withContext(io) {
        mutex.withLock {
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "$what failed", e)
                onFailure
            }
        }
    }

    private fun titleOrHost(title: String, url: String): String =
        title.trim().ifEmpty { runCatching { URI(url).host }.getOrNull() ?: url }.take(MAX_TITLE_LENGTH)

    companion object {
        private const val TAG = "LibraryRepository"
        private const val HISTORY_LIMIT = 5000
        private const val CHUNK = 500
        private const val DAY_MILLIS = 24L * 3_600_000

        fun create(context: Context, icons: IconRetention? = null) = LibraryRepository(TabDatabase.get(context).libraryDao(), icons = icons)
    }
}
