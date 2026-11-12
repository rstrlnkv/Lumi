package ru.digitalpunk.lumi.data.tabs

import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.RoomDatabase

/** The schema version; `data/schemas/<database>/<version>.json` must exist for every version up to it. */
const val TAB_DATABASE_VERSION = 2

/**
 * Every version bump needs an exported schema in `data/schemas/` and a migration; never a destructive fallback.
 * Version 2 added `tab_group` and the `groupId` and `currentGroupId` columns.
 */
@Database(
    entities = [TabEntity::class, TabSelectionEntity::class, TabGroupEntity::class],
    version = TAB_DATABASE_VERSION,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class TabDatabase : RoomDatabase() {
    abstract fun tabDao(): TabDao
}
