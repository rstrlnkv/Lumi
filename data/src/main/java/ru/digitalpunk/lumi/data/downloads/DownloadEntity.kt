package ru.digitalpunk.lumi.data.downloads

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A normal download. There is no private column: a private entry cannot be stored by type. */
@Entity(tableName = "download", indices = [Index("startedAt")])
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val mimeType: String,
    val uri: String?,
    val site: String,
    val totalBytes: Long,
    val doneBytes: Long,
    /** [DownloadState.name]; an unknown value reads as Failed. */
    val state: String,
    val startedAt: Long,
)
