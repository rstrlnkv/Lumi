package ru.digitalpunk.lumi.downloads

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.digitalpunk.lumi.data.downloads.DownloadItem
import ru.digitalpunk.lumi.data.downloads.DownloadRepository
import ru.digitalpunk.lumi.data.downloads.DownloadState
import ru.digitalpunk.lumi.data.downloads.DownloadStorage
import ru.digitalpunk.lumi.data.downloads.PrivateDownloads
import ru.digitalpunk.lumi.data.downloads.canRetry
import ru.digitalpunk.lumi.data.downloads.enoughSpace
import ru.digitalpunk.lumi.data.downloads.isActive
import ru.digitalpunk.lumi.data.downloads.isImageType
import ru.digitalpunk.lumi.data.downloads.mimeForName
import ru.digitalpunk.lumi.data.downloads.nameAndMimeAfterResponse
import ru.digitalpunk.lumi.data.downloads.sanitizeFileName
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.DownloadOffer
import ru.digitalpunk.lumi.engine.ImageTarget
import ru.digitalpunk.lumi.engine.nameFromUrl
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

sealed interface DownloadEvent {
    val item: DownloadItem

    data class Started(override val item: DownloadItem) : DownloadEvent
    data class Finished(override val item: DownloadItem) : DownloadEvent
    data class Failed(override val item: DownloadItem) : DownloadEvent
}

/**
 * Runs every download of the process (ADR 010): copies the body stream into the system Downloads folder on an IO thread.
 * [DownloadService] only keeps the process alive while [activeCount] is above 0. Normal entries go to Room, private ones stay
 * in memory and are dropped once no private tab is open. Call it from the main thread.
 */
class DownloadManager(
    private val context: Context,
    private val engine: BrowserEngine,
    private val repository: DownloadRepository,
    private val storage: DownloadStorage,
    private val notifier: DownloadNotifier,
    private val scope: CoroutineScope,
) {
    private val privates = PrivateDownloads()
    private val progress = MutableStateFlow<Map<Long, Long>>(emptyMap())
    private val jobs = HashMap<Long, Job>()
    private val streams = ConcurrentHashMap<Long, InputStream>()
    private val interrupted = HashSet<Long>()
    private val mutableActive = MutableStateFlow(0)
    private val mutableEvents = MutableSharedFlow<DownloadEvent>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val mutableMissing = MutableStateFlow<Set<Long>>(emptySet())
    private val mimeMap = MimeTypeMap.getSingleton()

    /** Running and queued downloads. */
    val activeCount: StateFlow<Int> = mutableActive

    /** Only while Lumi is on screen; with no collector they are dropped and the notification reports. A cancel sends nothing. */
    val events: SharedFlow<DownloadEvent> = mutableEvents

    /** Finished entries whose file is gone, as of the last [refreshMissing]. */
    val missing: StateFlow<Set<Long>> = mutableMissing

    /** Room entries, private entries and live progress, newest first. */
    val items: StateFlow<List<DownloadItem>> = combine(repository.observe(), privates.items, progress) { rows, private, done ->
        (rows + private).map { item -> done[item.id]?.let { item.copy(doneBytes = it) } ?: item }
            .sortedWith(compareByDescending<DownloadItem> { it.startedAt }.thenByDescending { it.id })
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        // Created before any download of this process: whatever is still active in Room died with an earlier process.
        scope.launch {
            val stale = repository.recoverInterrupted()
            withContext(Dispatchers.IO) { stale.forEach { storage.delete(it.uri) } }
        }
        scope.launch { engine.tabs.map { it.privateCount }.distinctUntilChanged().collect { if (it == 0) privates.dropFinished() } }
    }

    /** Saves a file the person confirmed in the ask sheet, from Gecko's own stream. */
    fun start(offer: DownloadOffer) {
        scope.launch {
            val item = try {
                create(entryFor(offer.url, offer.suggestedName, offer.mimeType, offer.contentLength, offer.site, offer.inPrivateTab))
            } catch (e: Exception) {
                offer.discard()
                Log.w(TAG, "Could not create a download entry", e)
                return@launch
            }
            run(item) { offer }
        }
    }

    /** "Save image" (M7c2): no ask sheet, fetched again through Gecko in the tab's context; a response that is not an image fails. */
    fun saveImage(image: ImageTarget) {
        scope.launch {
            run(create(imageEntry(image))) {
                engine.fetchForDownload(image.url, image.inPrivateTab).also {
                    if (!isImageType(it.mimeType)) {
                        it.discard()
                        throw IOException("Not an image: ${it.mimeType}")
                    }
                }
            }
        }
    }

    /** The storage permission was refused (API 26-28): the entry shows Failed so the person sees why. */
    fun refuse(offer: DownloadOffer) {
        offer.discard()
        refused(entryFor(offer.url, offer.suggestedName, offer.mimeType, offer.contentLength, offer.site, offer.inPrivateTab))
    }

    /** [refuse] for a Save image. */
    fun refuseImage(image: ImageTarget) = refused(imageEntry(image))

    private fun refused(entry: DownloadItem) {
        scope.launch { mutableEvents.tryEmit(DownloadEvent.Failed(create(entry.copy(state = DownloadState.Failed)))) }
    }

    /** Retry and "download again": fetched again, no ask sheet. A POST or cookie-only download may fail here. */
    fun retry(id: Long) {
        val item = items.value.firstOrNull { it.id == id } ?: return
        if (item.state.isActive || !canRetry(item)) return
        val again = item.copy(state = DownloadState.Running, doneBytes = 0, uri = null)
        scope.launch {
            write(again)
            run(again) { engine.fetchForDownload(again.url, again.isPrivate) }
        }
    }

    fun cancel(id: Long) {
        jobs[id]?.cancel()
        // A blocking read on Gecko's stream does not see coroutine cancellation; closing the stream ends it.
        streams[id]?.let { runCatching { it.close() } }
    }

    /** The system ended the foreground time (API 35+): running entries end Failed, with Retry. */
    fun interruptRunning() {
        jobs.keys.toList().forEach { id ->
            interrupted += id
            cancel(id)
        }
    }

    fun remove(id: Long) {
        if (id in jobs) return
        if (items.value.firstOrNull { it.id == id }?.isPrivate == true) privates.remove(id) else scope.launch { repository.delete(id) }
    }

    /** Undo of a remove or a delete. */
    fun restore(item: DownloadItem) {
        if (item.isPrivate) privates.restore(item) else scope.launch { repository.insert(item) }
    }

    fun deleteFile(item: DownloadItem) {
        scope.launch(Dispatchers.IO) { storage.delete(item.uri) }
    }

    /** "Clear list": every entry that is not in progress; files stay. */
    suspend fun clearList(): Int = privates.clearFinished() + repository.clearFinished()

    fun refreshMissing() {
        val done = items.value.filter { it.state == DownloadState.Done }
        scope.launch { mutableMissing.value = withContext(Dispatchers.IO) { done.filter { !storage.exists(it.uri) }.map { it.id }.toSet() } }
    }

    /** The sanitised name a file will be saved under; the ask sheet shows the same name. */
    fun fileName(url: String, suggested: String?, mime: String?): String =
        sanitizeFileName(suggested ?: nameFromUrl(url), FALLBACK_NAME, mime?.let(mimeMap::getExtensionFromMimeType))

    private fun entryFor(url: String, name: String?, mime: String?, length: Long, site: String?, private: Boolean): DownloadItem {
        val fileName = fileName(url, name, mime)
        return DownloadItem(
            0, url, fileName, mimeForName(fileName, mime, mimeMap::getMimeTypeFromExtension), null, site.orEmpty(),
            length, 0, DownloadState.Running, System.currentTimeMillis(), private,
        )
    }

    private fun imageEntry(image: ImageTarget) = entryFor(image.url, null, null, -1, image.site, image.inPrivateTab)

    private suspend fun create(item: DownloadItem): DownloadItem = if (item.isPrivate) privates.add(item) else item.copy(id = repository.insert(item))

    private suspend fun write(item: DownloadItem) {
        if (item.isPrivate) privates.update(item) else runCatching { repository.update(item) }.onFailure { Log.w(TAG, "Saving a download entry failed", it) }
    }

    private suspend fun run(entry: DownloadItem, source: suspend () -> DownloadOffer) {
        jobs[entry.id] = currentCoroutineContext().job
        mutableActive.value = jobs.size
        if (jobs.size == 1) ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        mutableEvents.tryEmit(DownloadEvent.Started(entry))
        notifier.progress(entry)
        var item = entry
        var target: DownloadStorage.Target? = null
        val end = try {
            val offer = source()
            nameAndMimeAfterResponse(item.fileName, item.mimeType, offer.mimeType, mimeMap::getExtensionFromMimeType, mimeMap::getMimeTypeFromExtension)
                .let { (name, mime) -> item = item.copy(fileName = name, mimeType = mime) }
            val stream = offer.take() ?: throw IOException("The response is gone")
            streams[entry.id] = stream
            if (item.totalBytes < 0) item = item.copy(totalBytes = offer.contentLength)
            val done = withContext(Dispatchers.IO) {
                stream.use { input ->
                    if (!enoughSpace(storage.freeBytes(), item.totalBytes)) throw IOException(NO_SPACE)
                    val file = storage.create(item.fileName, item.mimeType).also { target = it }
                    item = item.copy(uri = file.uri)
                    // The partial file is known, so a process death can clean it up on the next start.
                    if (!item.isPrivate) repository.update(item)
                    file.output.use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        val uiGate = ProgressGate(UI_INTERVAL_MS)
                        val notificationGate = ProgressGate(NOTIFICATION_INTERVAL_MS)
                        var done = 0L
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            val now = SystemClock.elapsedRealtime()
                            if (uiGate.pass(now)) progress.update { it + (entry.id to done) }
                            if (notificationGate.pass(now)) {
                                // A stream of unknown or lying length stops before it fills the disk.
                                if (!enoughSpace(storage.freeBytes(), item.totalBytes - done)) throw IOException(NO_SPACE)
                                notifier.progress(item.copy(doneBytes = done))
                            }
                        }
                        done
                    }
                }
            }
            val uri = target!!.uri
            val name = withContext(Dispatchers.IO) { storage.publish(uri, item.fileName) }
            item.copy(state = DownloadState.Done, fileName = name, doneBytes = done, totalBytes = done)
        } catch (e: Exception) {
            val cancelled = (e is CancellationException || !currentCoroutineContext().isActive) && interrupted.remove(entry.id).not()
            if (!cancelled) Log.w(TAG, "A download failed: ${e.javaClass.simpleName}")
            val partial = target?.uri
            withContext(NonCancellable + Dispatchers.IO) { storage.delete(partial) }
            item.copy(state = if (cancelled) DownloadState.Cancelled else DownloadState.Failed, uri = null)
        } finally {
            streams.remove(entry.id)
            jobs.remove(entry.id)
            mutableActive.value = jobs.size
        }
        withContext(NonCancellable) { finish(end) }
    }

    private suspend fun finish(item: DownloadItem) {
        write(item)
        progress.update { it - item.id }
        if (item.isPrivate && engine.tabs.value.privateCount == 0) privates.dropFinished()
        when (item.state) {
            DownloadState.Done -> {
                notifier.done(item)
                mutableEvents.tryEmit(DownloadEvent.Finished(item))
            }
            DownloadState.Failed -> {
                notifier.failed(item)
                mutableEvents.tryEmit(DownloadEvent.Failed(item))
            }
            else -> notifier.cancel(item)
        }
    }

    private companion object {
        const val TAG = "DownloadManager"
        const val FALLBACK_NAME = "download"
        const val BUFFER_SIZE = 64 * 1024
        const val UI_INTERVAL_MS = 250L
        const val NOTIFICATION_INTERVAL_MS = 1000L
        const val NO_SPACE = "Not enough free space"
    }
}
