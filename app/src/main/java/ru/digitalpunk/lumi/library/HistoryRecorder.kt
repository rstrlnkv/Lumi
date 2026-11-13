package ru.digitalpunk.lumi.library

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.data.library.LibraryRepository
import ru.digitalpunk.lumi.data.library.isRecordable
import ru.digitalpunk.lumi.engine.HistorySink

/**
 * Writes the engine's visits to the library. The engine calls it on the main thread, so events go into an unbounded
 * channel; one consumer keeps a visit before its title. Private tabs never call it (the engine gives them no `onVisited`).
 */
class HistoryRecorder(
    private val library: LibraryRepository,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) : HistorySink {
    private sealed interface Event {
        data class Visit(val url: String, val at: Long) : Event
        data class Title(val url: String, val title: String) : Event
    }

    private val events = Channel<Event>(Channel.UNLIMITED)
    private var started = false

    /** Seeds Favorites, prunes old visits, then starts writing. A second call (a new activity) does nothing. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            library.open()
            for (event in events) when (event) {
                is Event.Visit -> library.recordVisit(event.url, event.at)
                is Event.Title -> library.titleVisit(event.url, event.title)
            }
        }
    }

    override fun visited(url: String) {
        if (isRecordable(url)) events.trySend(Event.Visit(url, clock()))
    }

    override fun titled(url: String, title: String) {
        if (isRecordable(url)) events.trySend(Event.Title(url, title))
    }
}
