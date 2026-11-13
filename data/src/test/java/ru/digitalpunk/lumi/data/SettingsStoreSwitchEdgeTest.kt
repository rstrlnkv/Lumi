package ru.digitalpunk.lumi.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
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

class SettingsStoreSwitchEdgeTest {
    private val file = File(createTempDirectory("settings-switch").toFile(), "settings.preferences_pb")

    private fun <T> withStore(block: suspend (SettingsStore, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> T): T = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = PreferenceDataStoreFactory.create(scope = scope) { file }
            block(SettingsStore(ds), ds)
        } finally {
            scope.cancel()
        }
    }

    @Test fun storedKeyIsSwitchToNewTabsAndReadsBack() = withStore { store, ds ->
        ds.edit { it[booleanPreferencesKey("switch_to_new_tabs")] = true }
        assertEquals(true, store.switchToNewTabs.first())
        store.setSwitchToNewTabs(false)
        assertEquals(false, ds.data.first()[booleanPreferencesKey("switch_to_new_tabs")])
    }

    @Test fun settingTheSameValueTwiceKeepsIt() = withStore { store, _ ->
        store.setSwitchToNewTabs(true)
        store.setSwitchToNewTabs(true)
        assertEquals(true, store.switchToNewTabs.first())
    }

    @Test fun aWrongTypeStoredUnderTheKeyFallsBackToOff() = withStore { store, ds ->
        ds.edit { it[stringPreferencesKey("switch_to_new_tabs")] = "yes" }
        assertEquals(false, store.switchToNewTabs.first())
    }
}
