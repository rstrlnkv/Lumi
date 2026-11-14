package ru.digitalpunk.lumi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.digitalpunk.lumi.data.library.LibraryIds
import ru.digitalpunk.lumi.data.search.SearchEngine

enum class AddressBarStyle(internal val stored: String) {
    Compact("compact"),
    Classic("classic"),
}

/** The tab the Library opens on: the one that was open last. */
enum class LibraryTab(internal val stored: String) {
    Bookmarks("bookmarks"),
    History("history"),
    Downloads("downloads"),
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

    /** The folder the start page shows, by id. [LibraryIds.FAVORITES] when nothing, a blank or over-long id, or another type is stored. */
    val startPageFolder: Flow<String> = store.data.map { prefs ->
        prefs.read(START_PAGE_FOLDER)?.takeIf { it.isNotBlank() && it.length <= MAX_FOLDER_ID_LENGTH } ?: LibraryIds.FAVORITES
    }

    suspend fun setStartPageFolder(id: String) {
        store.edit { it[START_PAGE_FOLDER] = id }
    }

    /** The engine for searches and suggestions; [SearchEngine.Google] when nothing, something unknown or another type is stored. */
    val searchEngine: Flow<SearchEngine> = store.data.map { SearchEngine.fromId(it.read(SEARCH_ENGINE)) }

    suspend fun setSearchEngine(engine: SearchEngine) {
        store.edit { it[SEARCH_ENGINE] = engine.id }
    }

    /** "Search engine suggestions": what is typed in the address bar goes to the engine. On when nothing is stored. */
    val searchSuggestions: Flow<Boolean> = store.data.map { it.read(SEARCH_SUGGESTIONS) ?: true }

    suspend fun setSearchSuggestions(value: Boolean) {
        store.edit { it[SEARCH_SUGGESTIONS] = value }
    }

    /** Lumi asked for the notification permission at a first download; it never asks again. False for a value of another type. */
    val downloadNotificationsAsked: Flow<Boolean> = store.data.map { it.read(DOWNLOAD_NOTIFICATIONS_ASKED) ?: false }

    suspend fun setDownloadNotificationsAsked() {
        store.edit { it[DOWNLOAD_NOTIFICATIONS_ASKED] = true }
    }

    /** Starts from the launcher, for the default-browser card; 0 when nothing, a negative value or another type is stored. */
    val launchCount: Flow<Int> = store.data.map { it.read(LAUNCH_COUNT)?.coerceAtLeast(0) ?: 0 }

    /** Adds one launch; stops at [MAX_LAUNCH_COUNT]. */
    suspend fun countLaunch() {
        store.edit { it[LAUNCH_COUNT] = ((it.read(LAUNCH_COUNT) ?: 0).coerceAtLeast(0) + 1).coerceAtMost(MAX_LAUNCH_COUNT) }
    }

    /** "Not now" on the default-browser card: it never comes back. False for a value of another type. */
    val defaultCardDismissed: Flow<Boolean> = store.data.map { it.read(DEFAULT_CARD_DISMISSED) ?: false }

    suspend fun setDefaultCardDismissed() {
        store.edit { it[DEFAULT_CARD_DISMISSED] = true }
    }

    /** How often in a row the person refused the system's default-browser dialog, 0..[MAX_ROLE_REFUSALS]; 0 for another type. */
    val roleRefusals: Flow<Int> = store.data.map { (it.read(ROLE_REFUSALS) ?: 0).coerceIn(0, MAX_ROLE_REFUSALS) }

    suspend fun setRoleRefusals(count: Int) {
        store.edit { it[ROLE_REFUSALS] = count.coerceIn(0, MAX_ROLE_REFUSALS) }
    }

    /** Favorites got its first-run sites (or already had items); false for a value of another type. */
    val favoritesSeeded: Flow<Boolean> = store.data.map { it.read(FAVORITES_SEEDED) ?: false }

    suspend fun setFavoritesSeeded() {
        store.edit { it[FAVORITES_SEEDED] = true }
    }

    /** GeckoView's stored local network denials from before Lumi asked were reset once (ADR 015); false for another type. */
    val lnaDenialsReset: Flow<Boolean> = store.data.map { it.read(LNA_DENIALS_RESET) ?: false }

    suspend fun setLnaDenialsReset() {
        store.edit { it[LNA_DENIALS_RESET] = true }
    }

    /** "Check for updates" in Settings; on when nothing, or another type, is stored. */
    val updatesOn: Flow<Boolean> = store.data.map { it.read(UPDATES_ON) ?: true }

    suspend fun setUpdatesOn(value: Boolean) {
        store.edit { it[UPDATES_ON] = value }
    }

    /** The chosen update channel's name; null means the installed build's channel. */
    val updateChannel: Flow<String?> = store.data.map { it.read(UPDATE_CHANNEL) }

    suspend fun setUpdateChannel(name: String) {
        store.edit { it[UPDATE_CHANNEL] = name }
    }

    /** When the last update check succeeded, in epoch milliseconds; null when it never did. */
    val lastUpdateCheck: Flow<Long?> = store.data.map { it.read(LAST_UPDATE_CHECK) }

    suspend fun setLastUpdateCheck(millis: Long) {
        store.edit { it[LAST_UPDATE_CHECK] = millis }
    }

    /** The release tag of the last update notification, so one build is announced once. */
    val notifiedUpdateTag: Flow<String?> = store.data.map { it.read(NOTIFIED_UPDATE_TAG) }

    suspend fun setNotifiedUpdateTag(tag: String) {
        store.edit { it[NOTIFIED_UPDATE_TAG] = tag }
    }

    private companion object {
        val ADDRESS_BAR_STYLE = stringPreferencesKey("address_bar_style")
        val PROTECTION_OFF_HOSTS = stringSetPreferencesKey("protection_off_hosts")
        val SWITCH_TO_NEW_TABS = booleanPreferencesKey("switch_to_new_tabs")
        val LIBRARY_TAB = stringPreferencesKey("library_tab")
        val START_PAGE_FOLDER = stringPreferencesKey("start_page_folder")
        val SEARCH_ENGINE = stringPreferencesKey("search_engine")
        val SEARCH_SUGGESTIONS = booleanPreferencesKey("search_suggestions")
        val DOWNLOAD_NOTIFICATIONS_ASKED = booleanPreferencesKey("download_notifications_asked")
        val LAUNCH_COUNT = intPreferencesKey("launch_count")
        val DEFAULT_CARD_DISMISSED = booleanPreferencesKey("default_card_dismissed")
        val ROLE_REFUSALS = intPreferencesKey("default_role_refusals")
        val FAVORITES_SEEDED = booleanPreferencesKey("favorites_seeded")
        val LNA_DENIALS_RESET = booleanPreferencesKey("lna_denials_reset")
        val UPDATES_ON = booleanPreferencesKey("updates_on")
        val UPDATE_CHANNEL = stringPreferencesKey("update_channel")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val NOTIFIED_UPDATE_TAG = stringPreferencesKey("notified_update_tag")
    }
}

/** The value under [key], or null when a value of another type is stored there (a corrupt file or a downgrade). */
private inline fun <reified T> Preferences.read(key: Preferences.Key<T>): T? = asMap()[key] as? T

private const val MAX_FOLDER_ID_LENGTH = 64
private const val MAX_LAUNCH_COUNT = 1_000
/** After this many refusals in a row the system stops showing its dialog, so Lumi opens the default-apps screen. */
const val MAX_ROLE_REFUSALS = 2
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
