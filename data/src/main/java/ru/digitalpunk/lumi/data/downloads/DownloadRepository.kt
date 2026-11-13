package ru.digitalpunk.lumi.data.downloads

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.digitalpunk.lumi.data.tabs.TabDatabase

/** Normal downloads in `tabs.db`. Private entries are refused: they live in [PrivateDownloads]. */
class DownloadRepository(private val dao: DownloadDao) {
    /** Newest first. A broken read ends the flow instead of crashing the observer. */
    fun observe(): Flow<List<DownloadItem>> =
        dao.observe().map { rows -> rows.map { it.toItem() } }.catch { Log.w(TAG, "Reading downloads failed", it) }

    /** Inserts [item] (keeping its id when it has one, for Undo) and returns the id. */
    suspend fun insert(item: DownloadItem): Long {
        val entity = item.toEntity()
        return withContext(Dispatchers.IO) { dao.insert(entity) }
    }

    suspend fun update(item: DownloadItem) {
        val entity = item.toEntity()
        withContext(Dispatchers.IO) { dao.update(entity) }
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }

    /** Removes every entry that is not in progress; files stay. */
    suspend fun clearFinished(): Int = withContext(Dispatchers.IO) { dao.deleteFinished() }

    /** After a process death nothing can resume: active rows become Failed. Returns them, so their partial files can be deleted. */
    suspend fun recoverInterrupted(): List<DownloadItem> = withContext(Dispatchers.IO) {
        dao.active().map { it.toItem() }.also { dao.failInterrupted() }
    }

    companion object {
        private const val TAG = "DownloadRepository"

        fun create(context: Context) = DownloadRepository(TabDatabase.get(context).downloadDao())
    }
}
