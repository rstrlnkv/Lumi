package ru.digitalpunk.lumi.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStoreFavoritesSeededTest {
    private val file = File(createTempDirectory("settings-seeded").toFile(), "settings.preferences_pb")

    @Test fun favoritesSeededIsFalseUntilSetAndForAWrongType() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = PreferenceDataStoreFactory.create(scope = scope) { file }
            val store = SettingsStore(ds)
            assertEquals(false, store.favoritesSeeded.first())
            ds.edit { it[stringPreferencesKey("favorites_seeded")] = "true" }
            assertEquals(false, store.favoritesSeeded.first())
            ds.edit { it.remove(stringPreferencesKey("favorites_seeded")) }
            store.setFavoritesSeeded()
            assertEquals(true, store.favoritesSeeded.first())
        } finally {
            scope.cancel()
        }
    }
}
