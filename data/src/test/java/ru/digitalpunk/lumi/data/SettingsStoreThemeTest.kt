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

class SettingsStoreThemeTest {
    private val file = File(createTempDirectory("settings-theme").toFile(), "settings.preferences_pb")

    private fun withStore(block: suspend (SettingsStore, DataStore<Preferences>) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = PreferenceDataStoreFactory.create(scope = scope) { file }
            block(SettingsStore(ds), ds)
        } finally {
            scope.cancel()
        }
    }

    @Test fun `nothing stored is system theme and wallpaper colours on`() = withStore { store, _ ->
        assertEquals(ThemeChoice.System, store.theme.first())
        assertEquals(true, store.wallpaperColors.first())
    }

    @Test fun `each value is kept`() = withStore { store, ds ->
        store.setTheme(ThemeChoice.Dark)
        store.setWallpaperColors(false)
        assertEquals(ThemeChoice.Dark, store.theme.first())
        assertEquals(false, store.wallpaperColors.first())
        assertEquals("dark", ds.data.first()[stringPreferencesKey("theme")])
        store.setTheme(ThemeChoice.Light)
        assertEquals(ThemeChoice.Light, store.theme.first())
    }

    @Test fun `an unknown theme reads system`() = withStore { store, ds ->
        ds.edit { it[stringPreferencesKey("theme")] = "sepia" }
        assertEquals(ThemeChoice.System, store.theme.first())
    }

    @Test fun `a wrong type under either key reads the default`() = withStore { store, ds ->
        ds.edit {
            it[intPreferencesKey("theme")] = 2
            it[stringPreferencesKey("wallpaper_colors")] = "no"
        }
        assertEquals(ThemeChoice.System, store.theme.first())
        assertEquals(true, store.wallpaperColors.first())
    }
}
