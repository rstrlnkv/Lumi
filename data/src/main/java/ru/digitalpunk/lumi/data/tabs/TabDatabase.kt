package ru.digitalpunk.lumi.data.tabs

import androidx.room.Database
import androidx.room.RoomDatabase

/** Every version bump needs an exported schema in `data/schemas/` and a migration; never a destructive fallback. */
@Database(entities = [TabEntity::class, TabSelectionEntity::class], version = 1, exportSchema = true)
abstract class TabDatabase : RoomDatabase() {
    abstract fun tabDao(): TabDao
}
