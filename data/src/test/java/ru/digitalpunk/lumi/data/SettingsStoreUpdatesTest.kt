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
import kotlin.test.assertNull

class SettingsStoreUpdatesTest {
    private val file = File(createTempDirectory("settings-updates").toFile(), "settings.preferences_pb")

    private fun withStore(block: suspend (SettingsStore, DataStore<Preferences>) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = PreferenceDataStoreFactory.create(scope = scope) { file }
            block(SettingsStore(ds), ds)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `nothing stored is on, no channel, never checked, nothing notified`() = withStore { store, _ ->
        assertEquals(true, store.updatesOn.first())
        assertNull(store.updateChannel.first())
        assertNull(store.lastUpdateCheck.first())
        assertNull(store.notifiedUpdateTag.first())
    }

    @Test fun `each value is kept`() = withStore { store, _ ->
        store.setUpdatesOn(false)
        store.setUpdateChannel("BETA")
        store.setLastUpdateCheck(1_763_100_000_000)
        store.setNotifiedUpdateTag("v0.1.0.DEV.3613700")
        assertEquals(false, store.updatesOn.first())
        assertEquals("BETA", store.updateChannel.first())
        assertEquals(1_763_100_000_000, store.lastUpdateCheck.first())
        assertEquals("v0.1.0.DEV.3613700", store.notifiedUpdateTag.first())
    }

    @Test fun `a wrong type under a key reads as the default`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = PreferenceDataStoreFactory.create(scope = scope) { file }
            ds.edit {
                it[stringPreferencesKey("updates_on")] = "no"
                it[intPreferencesKey("update_channel")] = 1
                it[stringPreferencesKey("last_update_check")] = "yesterday"
                it[intPreferencesKey("notified_update_tag")] = 2
            }
            val store = SettingsStore(ds)
            assertEquals(true, store.updatesOn.first())
            assertNull(store.updateChannel.first())
            assertNull(store.lastUpdateCheck.first())
            assertNull(store.notifiedUpdateTag.first())
        } finally {
            scope.cancel()
        }
    }
}
