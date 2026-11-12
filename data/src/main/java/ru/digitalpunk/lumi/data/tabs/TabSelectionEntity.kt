package ru.digitalpunk.lumi.data.tabs

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A single row (`id = 0`) that names the selected tab. */
@Entity(tableName = "tab_selection")
data class TabSelectionEntity(
    @PrimaryKey val id: Int = 0,
    val tabId: String?,
)
