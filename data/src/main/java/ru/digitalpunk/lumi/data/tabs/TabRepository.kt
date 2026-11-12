package ru.digitalpunk.lumi.data.tabs

import android.content.Context
import android.util.Log
import androidx.room.Room
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** A tab as stored. [sessionState] is the Gecko session JSON; [stateChanged] says whether it must be written again. */
data class StoredTab(
    val id: String,
    val url: String,
    val title: String,
    val sessionState: String?,
    val stateChanged: Boolean = true,
    val groupId: String? = null,
)

data class StoredGroup(val id: String, val name: String)

data class StoredTabs(
    val tabs: List<StoredTab>,
    val selectedId: String?,
    val groups: List<StoredGroup> = emptyList(),
    val currentGroupId: String? = null,
)

/** Tab rows in Room, session states as files (they are too big for a cursor window). */
class TabRepository(
    private val dao: TabDao,
    private val stateFiles: BlobFiles,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    /** Null when nothing is stored or reading failed; the caller then starts with one empty tab. */
    suspend fun load(): StoredTabs? = withContext(io) {
        mutex.withLock {
            try {
                val rows = dao.tabs()
                if (rows.isEmpty()) return@withLock null
                StoredTabs(
                    rows.map { StoredTab(it.id, it.url, it.title, stateFiles.read(it.id)?.decodeToString(), stateChanged = false, groupId = it.groupId) },
                    dao.selectedTabId(),
                    dao.groups().map { StoredGroup(it.id, it.name) },
                    dao.currentGroupId(),
                )
            } catch (e: Exception) {
                Log.e(TAG, "Loading tabs failed", e)
                null
            }
        }
    }

    /** Returns false when saving failed (the rows, or a state file that fits the limit); the caller tries again on the next change. */
    suspend fun save(
        tabs: List<StoredTab>,
        selectedId: String?,
        groups: List<StoredGroup> = emptyList(),
        currentGroupId: String? = null,
    ): Boolean = withContext(io) {
        mutex.withLock {
            try {
                val stamps = dao.tabs().associate { it.id to it.lastActiveAt }
                dao.replaceAll(
                    tabs.mapIndexed { i, t ->
                        val stamp = if (t.id == selectedId) now() else stamps[t.id] ?: 0L
                        TabEntity(t.id, i, t.url, t.title, stamp, t.groupId)
                    },
                    groups.mapIndexed { i, g -> TabGroupEntity(g.id, g.name, i) },
                    selectedId,
                    currentGroupId,
                )
                var allWritten = true
                tabs.filter { it.stateChanged }.forEach { t ->
                    val state = t.sessionState
                    if (state == null) {
                        stateFiles.delete(t.id)
                    } else {
                        val bytes = state.encodeToByteArray()
                        // Over the limit is dropped on purpose; any other refusal is a failure to retry.
                        if (!stateFiles.write(t.id, bytes) && bytes.size <= stateFiles.maxBytes) allWritten = false
                    }
                }
                stateFiles.sweep(tabs.mapTo(HashSet()) { it.id })
                allWritten
            } catch (e: Exception) {
                Log.e(TAG, "Saving tabs failed", e)
                false
            }
        }
    }

    companion object {
        private const val TAG = "TabRepository"
        private const val MAX_STATE_BYTES = 1024 * 1024

        /** The real thing: Room database `tabs.db` and session states in `filesDir/tab-state`. */
        fun create(context: Context): TabRepository {
            val db = Room.databaseBuilder(context, TabDatabase::class.java, "tabs.db").build()
            return TabRepository(db.tabDao(), BlobFiles(File(context.filesDir, "tab-state"), "json", MAX_STATE_BYTES))
        }
    }
}
