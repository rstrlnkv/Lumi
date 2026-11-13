package ru.digitalpunk.lumi.data.library

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** The fixed ids of the bookmark tree. The root has no row; Favorites is a row seeded by [LibraryRepository.open]. */
object LibraryIds {
    const val ROOT = "root"
    const val FAVORITES = "favorites"
}

/**
 * A folder or a bookmark. Folders and bookmarks share one manual order ([position]) per parent.
 * [title] is empty only for a Favorites that was never renamed, so its name follows the language.
 */
@Entity(tableName = "bookmark_node", indices = [Index(value = ["parentId", "position"])])
data class BookmarkNodeEntity(
    @PrimaryKey val id: String,
    val parentId: String,
    val kind: Int,
    val title: String,
    val url: String?,
    val position: Int,
    val createdAt: Long,
) {
    val isFolder: Boolean get() = kind == FOLDER

    companion object {
        const val FOLDER = 0
        const val BOOKMARK = 1
    }
}
