package ru.digitalpunk.lumi.data.tabs

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A named tab group. [position] is the creation order. No foreign key: a tab whose group is missing is handled when it is restored. */
@Entity(tableName = "tab_group")
data class TabGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val position: Int,
)
