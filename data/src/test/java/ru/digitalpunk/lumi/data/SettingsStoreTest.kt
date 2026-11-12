package ru.digitalpunk.lumi.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SettingsStoreTest {
    private val dir = createTempDirectory("settings").toFile()
    private val file = File(dir, "settings.preferences_pb")

    private fun dataStore(scope: CoroutineScope) = PreferenceDataStoreFactory.create(scope = scope) { file }

    private fun <T> withStore(block: suspend (SettingsStore, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> T): T = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = dataStore(scope)
            block(SettingsStore(ds), ds)
        } finally {
            scope.cancel()
        }
    }

    @Test fun defaultsToCompact() = withStore { store, _ ->
        assertEquals(AddressBarStyle.Compact, store.addressBarStyle.first())
    }

    @Test fun returnsTheStyleThatWasSet() = withStore { store, _ ->
        store.setAddressBarStyle(AddressBarStyle.Classic)
        assertEquals(AddressBarStyle.Classic, store.addressBarStyle.first())
    }

    @Test fun unknownStoredValueFallsBackToCompact() = withStore { store, ds ->
        ds.edit { it[stringPreferencesKey("address_bar_style")] = "tabs-on-the-moon" }
        assertEquals(AddressBarStyle.Compact, store.addressBarStyle.first())
    }

    @Test fun styleSurvivesANewStoreOnTheSameFile() {
        withStore { store, _ -> store.setAddressBarStyle(AddressBarStyle.Classic) }
        withStore { store, _ -> assertEquals(AddressBarStyle.Classic, store.addressBarStyle.first()) }
    }
}
