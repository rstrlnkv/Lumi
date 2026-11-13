package ru.digitalpunk.lumi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class AddressBarStyle(internal val stored: String) {
    Compact("compact"),
    Classic("classic"),
}

/** The tab the Library opens on: the one that was open last. */
enum class LibraryTab(internal val stored: String) {
    Bookmarks("bookmarks"),
    History("history"),
}

private val Context.settingsDataStore by preferencesDataStore("settings")

/** App settings that outlive the process. Nothing from a page or a private tab goes here. */
class SettingsStore internal constructor(private val store: DataStore<Preferences>) {
    constructor(context: Context) : this(context.applicationContext.settingsDataStore)

    /** [AddressBarStyle.Compact] when nothing, or something unknown, is stored. */
    val addressBarStyle: Flow<AddressBarStyle> = store.data.map { prefs ->
        AddressBarStyle.entries.firstOrNull { it.stored == prefs[ADDRESS_BAR_STYLE] } ?: AddressBarStyle.Compact
    }

    suspend fun setAddressBarStyle(style: AddressBarStyle) {
        store.edit { it[ADDRESS_BAR_STYLE] = style.stored }
    }

    /** "Switch to new tabs": a link opened in a new tab is shown at once instead of in the background. Off when nothing is stored. */
    val switchToNewTabs: Flow<Boolean> = store.data.map { it.read(SWITCH_TO_NEW_TABS) ?: false }

    suspend fun setSwitchToNewTabs(value: Boolean) {
        store.edit { it[SWITCH_TO_NEW_TABS] = value }
    }

    /** Sites where the user turned tracker protection off: bare hosts, never a URL. Empty when nothing is stored. */
    val protectionOffHosts: Flow<Set<String>> = store.data.map { it.read(PROTECTION_OFF_HOSTS).orEmpty() }

    /** For a normal tab's site only; a private tab never writes here. */
    suspend fun setProtectionOff(host: String, off: Boolean) {
        store.edit { it[PROTECTION_OFF_HOSTS] = it[PROTECTION_OFF_HOSTS].orEmpty().toggled(host, off) }
    }

    /** [LibraryTab.Bookmarks] when nothing, something unknown or a value of another type is stored. */
    val libraryTab: Flow<LibraryTab> = store.data.map { prefs ->
        val stored = prefs.read(LIBRARY_TAB)
        LibraryTab.entries.firstOrNull { it.stored == stored } ?: LibraryTab.Bookmarks
    }

    suspend fun setLibraryTab(tab: LibraryTab) {
        store.edit { it[LIBRARY_TAB] = tab.stored }
    }

    private companion object {
        val ADDRESS_BAR_STYLE = stringPreferencesKey("address_bar_style")
        val PROTECTION_OFF_HOSTS = stringSetPreferencesKey("protection_off_hosts")
        val SWITCH_TO_NEW_TABS = booleanPreferencesKey("switch_to_new_tabs")
        val LIBRARY_TAB = stringPreferencesKey("library_tab")
    }
}

/** The value under [key], or null when a value of another type is stored there (a corrupt file or a downgrade). */
private inline fun <reified T> Preferences.read(key: Preferences.Key<T>): T? = asMap()[key] as? T

private const val MAX_PROTECTION_OFF_HOSTS = 500
private const val MAX_HOST_LENGTH = 253

/** The set with [host] (lower case, no trailing dot, no leading `www.`) added or removed. A blank or over-long host, or a 501st one, changes nothing. */
internal fun Set<String>.toggled(host: String, off: Boolean): Set<String> {
    val key = host.trim().lowercase().removePrefix("www.").removeSuffix(".")
    if (key.isEmpty() || key.length > MAX_HOST_LENGTH) return this
    return when {
        !off -> this - key
        key in this || size >= MAX_PROTECTION_OFF_HOSTS -> this
        else -> this + key
    }
}
