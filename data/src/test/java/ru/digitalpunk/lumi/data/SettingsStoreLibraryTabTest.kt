package ru.digitalpunk.lumi.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
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

class SettingsStoreLibraryTabTest {
    private val file = File(createTempDirectory("settings").toFile(), "settings.preferences_pb")

    private fun withStore(block: suspend (SettingsStore, androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) -> Unit) = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val ds = PreferenceDataStoreFactory.create(scope = scope) { file }
            block(SettingsStore(ds), ds)
        } finally {
            scope.cancel()
        }
    }

    @Test fun defaultsToBookmarks() = withStore { store, _ -> assertEquals(LibraryTab.Bookmarks, store.libraryTab.first()) }

    @Test fun returnsTheTabThatWasSetAfterANewStoreOnTheSameFile() {
        withStore { store, _ -> store.setLibraryTab(LibraryTab.History) }
        withStore { store, _ -> assertEquals(LibraryTab.History, store.libraryTab.first()) }
    }

    @Test fun theDownloadsTabSurvivesANewStoreOnTheSameFile() {
        withStore { store, _ -> store.setLibraryTab(LibraryTab.Downloads) }
        withStore { store, _ -> assertEquals(LibraryTab.Downloads, store.libraryTab.first()) }
    }

    @Test fun theNotificationAskIsRememberedAndAWrongTypedValueReadsAsNotAsked() {
        withStore { store, _ -> assertEquals(false, store.downloadNotificationsAsked.first()) }
        withStore { store, ds ->
            ds.edit { it[stringPreferencesKey("download_notifications_asked")] = "yes" }
            assertEquals(false, store.downloadNotificationsAsked.first())
            store.setDownloadNotificationsAsked()
            assertEquals(true, store.downloadNotificationsAsked.first())
        }
    }

    @Test fun anUnknownStoredValueFallsBackToBookmarks() = withStore { store, ds ->
        ds.edit { it[stringPreferencesKey("library_tab")] = "downloads-from-the-future" }
        assertEquals(LibraryTab.Bookmarks, store.libraryTab.first())
    }

    @Test fun aValueOfAnotherTypeFallsBackToBookmarks() = withStore { store, ds ->
        ds.edit { it[intPreferencesKey("library_tab")] = 1 }
        assertEquals(LibraryTab.Bookmarks, store.libraryTab.first())
    }
}
