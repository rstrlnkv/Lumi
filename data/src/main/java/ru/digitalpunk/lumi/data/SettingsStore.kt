package ru.digitalpunk.lumi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
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

    /** Sites where the user turned tracker protection off: bare hosts, never a URL. Empty when nothing is stored. */
    val protectionOffHosts: Flow<Set<String>> = store.data.map { it[PROTECTION_OFF_HOSTS].orEmpty() }

    /** For a normal tab's site only; a private tab never writes here. */
    suspend fun setProtectionOff(host: String, off: Boolean) {
        store.edit { it[PROTECTION_OFF_HOSTS] = it[PROTECTION_OFF_HOSTS].orEmpty().toggled(host, off) }
    }

    private companion object {
        val ADDRESS_BAR_STYLE = stringPreferencesKey("address_bar_style")
        val PROTECTION_OFF_HOSTS = stringSetPreferencesKey("protection_off_hosts")
    }
}

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
