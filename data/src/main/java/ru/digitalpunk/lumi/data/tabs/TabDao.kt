package ru.digitalpunk.lumi.data.tabs

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface TabDao {
    @Query("SELECT * FROM tab ORDER BY position")
    suspend fun tabs(): List<TabEntity>

    @Query("SELECT tabId FROM tab_selection WHERE id = 0")
    suspend fun selectedTabId(): String?

    @Query("DELETE FROM tab")
    suspend fun deleteTabs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabs(tabs: List<TabEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSelection(selection: TabSelectionEntity)

    @Transaction
    suspend fun replaceAll(tabs: List<TabEntity>, selected: String?) {
        deleteTabs()
        insertTabs(tabs)
        insertSelection(TabSelectionEntity(tabId = selected))
    }
}
