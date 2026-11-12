package ru.digitalpunk.lumi.data.tabs

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per open tab. The Gecko session state is a file, not a column (see [BlobFiles]). */
@Entity(tableName = "tab")
data class TabEntity(
    @PrimaryKey val id: String,
    val position: Int,
    val url: String,
    val title: String,
    val lastActiveAt: Long,
)
