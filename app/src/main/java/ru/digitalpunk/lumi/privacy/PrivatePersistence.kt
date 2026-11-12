package ru.digitalpunk.lumi.privacy

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.data.privatetabs.PrivateLoad
import ru.digitalpunk.lumi.data.privatetabs.PrivateStoredTab
import ru.digitalpunk.lumi.data.privatetabs.PrivateTabStore
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.PRIVATE_GROUP
import ru.digitalpunk.lumi.engine.SavedTab
import ru.digitalpunk.lumi.engine.TabId

/** Write only after the stored tabs are back (else an empty list would replace them), with a screen lock, and only a change. */
fun shouldWritePrivate(
    restored: Boolean,
    availability: LockAvailability,
    tabs: List<PrivateStoredTab>,
    lastSaved: List<PrivateStoredTab>?,
): Boolean = restored && availability == LockAvailability.Available && tabs != lastSaved

/** Lumi tells the person once only when stored private tabs could not come back; an empty start or a Retry tells nothing. */
fun tellsDropped(load: PrivateLoad): Boolean = load == PrivateLoad.Dropped

/** A wipe after the screen lock went away drops the private tabs only when some were stored or open. */
fun afterWipe(fileWiped: Boolean, openPrivate: Int): PrivateLoad =
    if (fileWiped || openPrivate > 0) PrivateLoad.Dropped else PrivateLoad.Empty

/**
 * Keeps the private tabs in the encrypted store: restores them, locked, after the normal tabs, saves them 2 s after a
 * change and when Lumi stops, and deletes the file at once when the last private tab closes. Without a screen lock
 * the private tabs and the store are wiped. Dropped tabs set [PrivateGate.dropNoticePending]. When the lock or the
 * key fails only for now, the file is left alone for the next start and nothing is written this run.
 */
class PrivatePersistence(
    private val engine: BrowserEngine,
    private val store: PrivateTabStore,
    private val gate: PrivateGate,
    private val scope: CoroutineScope,
) {
    private var started = false
    private var restored = false
    private var lastSaved: List<PrivateStoredTab>? = null

    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            engine.tabs.first { it.tabs.isNotEmpty() }
            val load = when (gate.availability()) {
                LockAvailability.Available -> store.load()
                LockAvailability.NoScreenLock -> afterWipe(store.wipe(), openPrivate = 0)
                LockAvailability.Unavailable -> PrivateLoad.Retry
            }
            if (load is PrivateLoad.Loaded) {
                engine.restorePrivateTabs(load.tabs.map { SavedTab(TabId(it.id), it.url, it.title, it.sessionState, PRIVATE_GROUP) })
            }
            if (tellsDropped(load)) gate.dropNoticePending = true
            if (load == PrivateLoad.Retry) return@launch
            lastSaved = engine.savePrivateSnapshot().tabs.map { it.stored() }
            restored = true
            launch { engine.changes.debounce(SAVE_DEBOUNCE_MILLIS).collect { save() } }
            launch { engine.tabs.map { it.privateCount }.distinctUntilChanged().filter { it == 0 }.collect { save() } }
        }
    }

    fun flushNow() = save()

    /** The screen lock may have been removed while Lumi was away: then nothing private stays. */
    fun onStart() {
        if (!restored || gate.availability() != LockAvailability.NoScreenLock) return
        val open = engine.tabs.value.privateCount
        engine.dropPrivateTabs()
        lastSaved = emptyList()
        scope.launch { if (tellsDropped(afterWipe(store.wipe(), open))) gate.dropNoticePending = true }
    }

    private fun save() {
        val tabs = engine.savePrivateSnapshot().tabs.map { it.stored() }
        if (!shouldWritePrivate(restored, gate.availability(), tabs, lastSaved)) return
        scope.launch { if (store.save(tabs)) lastSaved = tabs }
    }

    private fun SavedTab.stored() = PrivateStoredTab(id.value, url, title, sessionState)

    private companion object {
        const val SAVE_DEBOUNCE_MILLIS = 2_000L
    }
}
