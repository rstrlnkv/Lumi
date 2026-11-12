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

    @Query("SELECT * FROM tab_group ORDER BY position")
    suspend fun groups(): List<TabGroupEntity>

    @Query("SELECT currentGroupId FROM tab_selection WHERE id = 0")
    suspend fun currentGroupId(): String?

    @Query("DELETE FROM tab_group")
    suspend fun deleteGroups()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroups(groups: List<TabGroupEntity>)

    @Query("DELETE FROM tab")
    suspend fun deleteTabs()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabs(tabs: List<TabEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSelection(selection: TabSelectionEntity)

    @Transaction
    suspend fun replaceAll(tabs: List<TabEntity>, groups: List<TabGroupEntity>, selected: String?, currentGroupId: String?) {
        deleteTabs()
        deleteGroups()
        insertGroups(groups)
        insertTabs(tabs)
        insertSelection(TabSelectionEntity(tabId = selected, currentGroupId = currentGroupId))
    }
}
