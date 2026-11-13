package ru.digitalpunk.lumi.data.library

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One visit to a top-level page in a normal tab; repeated visits are collapsed only when read ([groupByDay]). */
@Entity(tableName = "history", indices = [Index("visitedAt"), Index("url")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val visitedAt: Long,
)
