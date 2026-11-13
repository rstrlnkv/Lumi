package ru.digitalpunk.lumi.data.tabs

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ru.digitalpunk.lumi.data.library.BookmarkNodeEntity
import ru.digitalpunk.lumi.data.library.HistoryEntity
import ru.digitalpunk.lumi.data.library.LibraryDao

/** The schema version; `data/schemas/<database>/<version>.json` must exist for every version up to it. */
const val TAB_DATABASE_VERSION = 3

/**
 * Every version bump needs an exported schema in `data/schemas/` and a migration; never a destructive fallback.
 * Version 2 added `tab_group` and the `groupId` and `currentGroupId` columns.
 * Version 3 added `bookmark_node` and `history` (the library, ADR 007).
 */
@Database(
    entities = [TabEntity::class, TabSelectionEntity::class, TabGroupEntity::class, BookmarkNodeEntity::class, HistoryEntity::class],
    version = TAB_DATABASE_VERSION,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3)],
)
abstract class TabDatabase : RoomDatabase() {
    abstract fun tabDao(): TabDao
    abstract fun libraryDao(): LibraryDao

    companion object {
        @Volatile private var instance: TabDatabase? = null

        /** The one `tabs.db` of the process: two builders on one file would break Room's invalidation and locking. */
        fun get(context: Context): TabDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, TabDatabase::class.java, "tabs.db").build().also { instance = it }
        }
    }
}
