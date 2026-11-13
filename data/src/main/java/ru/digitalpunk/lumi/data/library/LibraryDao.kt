package ru.digitalpunk.lumi.data.library

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM bookmark_node ORDER BY parentId, position")
    fun observeNodes(): Flow<List<BookmarkNodeEntity>>

    @Query("SELECT * FROM bookmark_node")
    suspend fun nodes(): List<BookmarkNodeEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(node: BookmarkNodeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(nodes: List<BookmarkNodeEntity>)

    @Update
    suspend fun update(node: BookmarkNodeEntity)

    @Query("DELETE FROM bookmark_node WHERE id IN (:ids)")
    suspend fun deleteNodes(ids: List<String>)

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM bookmark_node WHERE parentId = :parent")
    suspend fun nextPosition(parent: String): Int

    /** Inserts or replaces [node] at the end of its parent. */
    @Transaction
    suspend fun append(node: BookmarkNodeEntity) {
        insertAll(listOf(node.copy(position = nextPosition(node.parentId))))
    }

    @Insert
    suspend fun insertVisit(entry: HistoryEntity): Long

    @Query("UPDATE history SET title = :title WHERE id = (SELECT id FROM history WHERE url = :url ORDER BY visitedAt DESC, id DESC LIMIT 1)")
    suspend fun titleLatest(url: String, title: String)

    @Query("SELECT * FROM history ORDER BY visitedAt DESC, id DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<HistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVisits(entries: List<HistoryEntity>)

    @Query("DELETE FROM history WHERE id IN (:ids)")
    suspend fun deleteVisits(ids: List<Long>)

    @Query("DELETE FROM history WHERE visitedAt >= :from")
    suspend fun deleteVisitsSince(from: Long)

    @Query("DELETE FROM history")
    suspend fun deleteAllVisits()

    @Query("DELETE FROM history WHERE visitedAt < :cutoff")
    suspend fun pruneVisits(cutoff: Long)
}
