package ru.digitalpunk.lumi.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class SettingsStoreProtectionTwiceTest {
    private val file = File(createTempDirectory("settings-twice").toFile(), "settings.preferences_pb")

    private fun withStore(block: suspend (SettingsStore) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            block(SettingsStore(PreferenceDataStoreFactory.create(scope = scope) { file }))
        } finally {
            scope.cancel()
        }
    }

    @Test fun switchingOffTwiceStoresOneEntryAndOnTwiceStoresNone() = withStore { store ->
        store.setProtectionOff("a.com", off = true)
        store.setProtectionOff("A.com", off = true)
        assertEquals(setOf("a.com"), store.protectionOffHosts.first())
        store.setProtectionOff("a.com", off = false)
        store.setProtectionOff("www.a.com", off = false)
        assertEquals(emptySet(), store.protectionOffHosts.first())
    }

    @Test fun concurrentWritesOfDifferentHostsAllLand() = withStore { store ->
        kotlinx.coroutines.coroutineScope {
            repeat(20) { i -> launch(Dispatchers.IO) { store.setProtectionOff("h$i.com", off = true) } }
        }
        assertEquals(20, store.protectionOffHosts.first().size)
    }
}
