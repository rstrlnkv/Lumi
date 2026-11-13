package ru.digitalpunk.lumi.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

class SettingsStoreDefaultBrowserTest {
    private val file = File(createTempDirectory("settings-default").toFile(), "settings.preferences_pb")

    private fun withStore(block: suspend (SettingsStore, DataStore<Preferences>) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = PreferenceDataStoreFactory.create(scope = scope) { file }
            block(SettingsStore(ds), ds)
        } finally {
            scope.cancel()
        }
    }

    @Test fun launchCountStartsAtZeroAndCountsEachLaunch() = withStore { store, _ ->
        assertEquals(0, store.launchCount.first())
        store.countLaunch()
        store.countLaunch()
        assertEquals(2, store.launchCount.first())
    }

    @Test fun launchCountStopsAtOneThousand() = withStore { store, ds ->
        ds.edit { it[intPreferencesKey("launch_count")] = 1_000 }
        store.countLaunch()
        assertEquals(1_000, store.launchCount.first())
    }

    @Test fun aNegativeOrWrongTypedLaunchCountReadsZero() = withStore { store, ds ->
        ds.edit { it[intPreferencesKey("launch_count")] = -5 }
        assertEquals(0, store.launchCount.first())
        ds.edit { it.remove(intPreferencesKey("launch_count")); it[stringPreferencesKey("launch_count")] = "7" }
        assertEquals(0, store.launchCount.first())
    }

    @Test fun cardDismissalIsFalseUntilSetAndForAWrongType() = withStore { store, ds ->
        assertEquals(false, store.defaultCardDismissed.first())
        ds.edit { it[stringPreferencesKey("default_card_dismissed")] = "true" }
        assertEquals(false, store.defaultCardDismissed.first())
        ds.edit { it.remove(stringPreferencesKey("default_card_dismissed")) }
        store.setDefaultCardDismissed()
        assertEquals(true, store.defaultCardDismissed.first())
    }

    @Test fun roleRefusalsClampToZeroToTwoAndAWrongTypeReadsZero() = withStore { store, ds ->
        assertEquals(0, store.roleRefusals.first())
        store.setRoleRefusals(5)
        assertEquals(2, store.roleRefusals.first())
        store.setRoleRefusals(-1)
        assertEquals(0, store.roleRefusals.first())
        store.setRoleRefusals(1)
        assertEquals(1, store.roleRefusals.first())
        ds.edit { it.remove(intPreferencesKey("default_role_refusals")); it[stringPreferencesKey("default_role_refusals")] = "2" }
        assertEquals(0, store.roleRefusals.first())
    }

    @Test fun countingFromANegativeStoredValueRestartsAtOne() = withStore { store, ds ->
        ds.edit { it[intPreferencesKey("launch_count")] = -5 }
        store.countLaunch()
        assertEquals(1, store.launchCount.first())
        assertEquals(1, ds.data.first()[intPreferencesKey("launch_count")])
    }

    @Test fun countingFromAWrongTypedStoredValueStartsAtOne() = withStore { store, ds ->
        ds.edit { it[stringPreferencesKey("launch_count")] = "7" }
        store.countLaunch()
        assertEquals(1, store.launchCount.first())
    }

    @Test fun anOutOfRangeStoredRefusalCountReadsClamped() = withStore { store, ds ->
        ds.edit { it[intPreferencesKey("default_role_refusals")] = 9 }
        assertEquals(2, store.roleRefusals.first())
        ds.edit { it[intPreferencesKey("default_role_refusals")] = -4 }
        assertEquals(0, store.roleRefusals.first())
    }

    @Test fun theRefusalCountIsStoredClamped() = withStore { store, ds ->
        store.setRoleRefusals(9)
        assertEquals(2, ds.data.first()[intPreferencesKey("default_role_refusals")])
        store.setRoleRefusals(-9)
        assertEquals(0, ds.data.first()[intPreferencesKey("default_role_refusals")])
    }

    @Test fun aLongTypedLaunchCountReadsZeroInsteadOfCrashing() = withStore { store, ds ->
        ds.edit { it[androidx.datastore.preferences.core.longPreferencesKey("launch_count")] = 7L }
        assertEquals(0, store.launchCount.first())
        store.countLaunch()
        assertEquals(1, store.launchCount.first())
    }
}
