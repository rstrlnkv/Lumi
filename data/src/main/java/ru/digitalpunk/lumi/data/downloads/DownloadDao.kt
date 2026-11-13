package ru.digitalpunk.lumi.data.downloads

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Insert
    suspend fun insert(entity: DownloadEntity): Long

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("SELECT * FROM download ORDER BY startedAt DESC, id DESC")
    fun observe(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM download WHERE id = :id")
    suspend fun get(id: Long): DownloadEntity?

    @Query("DELETE FROM download WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM download WHERE state IN ('Queued', 'Running')")
    suspend fun active(): List<DownloadEntity>

    @Query("DELETE FROM download WHERE state NOT IN ('Queued', 'Running')")
    suspend fun deleteFinished(): Int

    @Query("UPDATE download SET state = 'Failed' WHERE state IN ('Queued', 'Running')")
    suspend fun failInterrupted()
}
