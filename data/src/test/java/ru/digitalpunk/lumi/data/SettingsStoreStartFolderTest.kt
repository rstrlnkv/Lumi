package ru.digitalpunk.lumi.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

class SettingsStoreStartFolderTest {
    private val file = File(createTempDirectory("settings").toFile(), "settings.preferences_pb")

    private fun withStore(block: suspend (SettingsStore, DataStore<Preferences>) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = PreferenceDataStoreFactory.create(scope = scope) { file }
            block(SettingsStore(ds), ds)
        } finally {
            scope.cancel()
        }
    }

    @Test fun defaultsToFavorites() = withStore { store, _ -> assertEquals("favorites", store.startPageFolder.first()) }

    @Test fun aSetFolderIsReadBackByANewStoreOnTheSameFile() {
        withStore { store, _ -> store.setStartPageFolder("f-travel") }
        withStore { store, _ -> assertEquals("f-travel", store.startPageFolder.first()) }
    }

    @Test fun aValueOfAnotherTypeFallsBackToFavorites() = withStore { store, ds ->
        ds.edit { it[intPreferencesKey("start_page_folder")] = 3 }
        assertEquals("favorites", store.startPageFolder.first())
    }

    @Test fun aBlankValueFallsBackToFavorites() = withStore { store, ds ->
        ds.edit { it[stringPreferencesKey("start_page_folder")] = "  " }
        assertEquals("favorites", store.startPageFolder.first())
    }

    @Test fun sixtyFourCharactersAreKeptAndSixtyFiveFallBack() = withStore { store, ds ->
        ds.edit { it[stringPreferencesKey("start_page_folder")] = "a".repeat(64) }
        assertEquals("a".repeat(64), store.startPageFolder.first())
        ds.edit { it[stringPreferencesKey("start_page_folder")] = "a".repeat(65) }
        assertEquals("favorites", store.startPageFolder.first())
    }
}
