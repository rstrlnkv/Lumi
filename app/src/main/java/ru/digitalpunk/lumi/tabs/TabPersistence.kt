package ru.digitalpunk.lumi.tabs

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.data.tabs.StoredTab
import ru.digitalpunk.lumi.data.tabs.TabRepository
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.SavedTab
import ru.digitalpunk.lumi.engine.TabId

private const val SAVE_DELAY_MILLIS = 2000L

/** Glue between the engine and storage: restores the tabs at start and saves them after changes. Main thread only. */
class TabPersistence(
    private val engine: BrowserEngine,
    private val repository: TabRepository,
    private val scope: CoroutineScope,
) {
    private var started = false
    private val savedVersions = HashMap<TabId, Long>()

    /** Restores the stored tabs (or opens one empty tab), then saves on every change. Safe to call again. */
    @OptIn(FlowPreview::class)
    fun start() {
        if (started) return
        started = true
        scope.launch {
            val stored = repository.load()
            if (stored != null) {
                engine.restoreTabs(
                    stored.tabs.map { SavedTab(TabId(it.id), it.url, it.title, it.sessionState) },
                    stored.selectedId?.let(::TabId),
                )
            }
            if (engine.tabs.value.tabs.isEmpty()) engine.newTab()
            engine.changes.debounce(SAVE_DELAY_MILLIS).collect { save() }
        }
    }

    /** Saves now, without the delay. Call it when the app leaves the screen. */
    fun flushNow() {
        if (started) scope.launch { save() }
    }

    private suspend fun save() {
        if (engine.tabs.value.tabs.isEmpty()) return
        val snapshot = engine.saveSnapshot()
        val ok = repository.save(
            snapshot.tabs.map {
                StoredTab(it.id.value, it.url, it.title, it.sessionState, stateChanged = snapshot.stateVersions[it.id] != savedVersions[it.id])
            },
            snapshot.selected?.value,
        )
        if (ok) {
            savedVersions.clear()
            savedVersions.putAll(snapshot.stateVersions)
        }
    }
}
