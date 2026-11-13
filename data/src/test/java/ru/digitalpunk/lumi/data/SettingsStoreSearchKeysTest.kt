package ru.digitalpunk.lumi.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.digitalpunk.lumi.data.search.SearchEngine
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

/** The stored key names are the file format: a rename would silently reset every person's choice. */
class SettingsStoreSearchKeysTest {
    private val file = File(createTempDirectory("settings-keys").toFile(), "settings.preferences_pb")

    private fun withStore(block: suspend (SettingsStore, DataStore<Preferences>) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = PreferenceDataStoreFactory.create(scope = scope) { file }
            block(SettingsStore(ds), ds)
        } finally {
            scope.cancel()
        }
    }

    @Test fun valuesStoredUnderTheShippedKeyNamesAreRead() = withStore { store, ds ->
        ds.edit {
            it[stringPreferencesKey("search_engine")] = "yandex"
            it[booleanPreferencesKey("search_suggestions")] = false
        }
        assertEquals(SearchEngine.Yandex, store.searchEngine.first())
        assertEquals(false, store.searchSuggestions.first())
    }

    @Test fun theSettersWriteUnderTheShippedKeyNames() = withStore { store, ds ->
        store.setSearchEngine(SearchEngine.Bing)
        store.setSearchSuggestions(false)
        val prefs = ds.data.first()
        assertEquals("bing", prefs[stringPreferencesKey("search_engine")])
        assertEquals(false, prefs[booleanPreferencesKey("search_suggestions")])
    }

    @Test fun aSetStoredWhereATextOrAFlagBelongsReadsAsTheDefault() = withStore { store, ds ->
        ds.edit {
            it[stringSetPreferencesKey("search_engine")] = setOf("bing")
            it[stringSetPreferencesKey("search_suggestions")] = setOf("false")
        }
        assertEquals(SearchEngine.Google, store.searchEngine.first())
        assertEquals(true, store.searchSuggestions.first())
    }
}
