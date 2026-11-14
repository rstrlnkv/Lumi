package ru.digitalpunk.lumi.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
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

class SettingsStoreClearChoiceTest {
    private val file = File(createTempDirectory("settings-clear").toFile(), "settings.preferences_pb")
    private val key = stringSetPreferencesKey("clear_data_choice")

    private fun withStore(block: suspend (SettingsStore, DataStore<Preferences>) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = PreferenceDataStoreFactory.create(scope = scope) { file }
            block(SettingsStore(ds), ds)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `nothing stored is history, cookies and cache`() = withStore { store, _ ->
        assertEquals(setOf(ClearItem.History, ClearItem.Cookies, ClearItem.Cache), store.clearDataChoice.first())
        assertEquals(DEFAULT_CLEAR_CHOICE, store.clearDataChoice.first())
    }

    @Test fun `a choice is kept`() = withStore { store, ds ->
        store.setClearDataChoice(setOf(ClearItem.Cache, ClearItem.Tabs))
        assertEquals(setOf(ClearItem.Cache, ClearItem.Tabs), store.clearDataChoice.first())
        assertEquals(setOf("cache", "tabs"), ds.data.first()[key])
    }

    @Test fun `unknown entries are dropped`() = withStore { store, ds ->
        ds.edit { it[key] = setOf("cookies", "passwords") }
        assertEquals(setOf(ClearItem.Cookies), store.clearDataChoice.first())
    }

    @Test fun `a set with no known entry reads the default`() = withStore { store, ds ->
        ds.edit { it[key] = setOf("passwords") }
        assertEquals(DEFAULT_CLEAR_CHOICE, store.clearDataChoice.first())
        ds.edit { it[key] = emptySet() }
        assertEquals(DEFAULT_CLEAR_CHOICE, store.clearDataChoice.first())
    }

    @Test fun `a wrong type reads the default`() = withStore { store, ds ->
        ds.edit { it[stringPreferencesKey("clear_data_choice")] = "cookies" }
        assertEquals(DEFAULT_CLEAR_CHOICE, store.clearDataChoice.first())
    }
}
