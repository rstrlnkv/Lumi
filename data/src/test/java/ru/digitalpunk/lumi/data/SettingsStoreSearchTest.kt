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
import ru.digitalpunk.lumi.data.search.SearchEngine
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsStoreSearchTest {
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

    @Test fun nothingStoredIsGoogleWithSuggestionsOn() = withStore { store, _ ->
        assertEquals(SearchEngine.Google, store.searchEngine.first())
        assertEquals(true, store.searchSuggestions.first())
    }

    @Test fun theChoiceAndTheSwitchAreKept() = withStore { store, _ ->
        store.setSearchEngine(SearchEngine.Yandex)
        store.setSearchSuggestions(false)
        assertEquals(SearchEngine.Yandex, store.searchEngine.first())
        assertEquals(false, store.searchSuggestions.first())
    }

    @Test fun anUnknownOrWrongTypedValueReadsAsTheDefault() = withStore { store, ds ->
        ds.edit { it[stringPreferencesKey("search_engine")] = "altavista" }
        assertEquals(SearchEngine.Google, store.searchEngine.first())
        ds.edit {
            it.remove(stringPreferencesKey("search_engine"))
            it[intPreferencesKey("search_engine")] = 3
            it[stringPreferencesKey("search_suggestions")] = "no"
        }
        assertEquals(SearchEngine.Google, store.searchEngine.first())
        assertEquals(true, store.searchSuggestions.first())
    }
}
