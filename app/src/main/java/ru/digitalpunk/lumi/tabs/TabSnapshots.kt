package ru.digitalpunk.lumi.tabs

import ru.digitalpunk.lumi.engine.isPrivate
import ru.digitalpunk.lumi.engine.TabsState
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.digitalpunk.lumi.data.tabs.SnapshotStore
import ru.digitalpunk.lumi.engine.BrowserEngine
import ru.digitalpunk.lumi.engine.TabId
import java.io.ByteArrayOutputStream

private const val CACHE_BYTES = 8 * 1024 * 1024
private const val QUALITY = 75
private const val MAX_DECODE_SIDE = 2048

/**
 * Page pictures for the tab grid: the engine's new snapshots go to a small memory cache and to disk; a closed tab's
 * picture and file are removed. All file work runs one job at a time, in order. Main thread only.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TabSnapshots(
    private val engine: BrowserEngine,
    private val store: SnapshotStore,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1),
) {
    private val cache = object : LruCache<TabId, ImageBitmap>(CACHE_BYTES) {
        override fun sizeOf(key: TabId, value: ImageBitmap) = value.width * value.height * 4
    }

    /** Bumped when a tab gets a new picture, so a card that reads it draws again. */
    private val versions = mutableStateMapOf<TabId, Int>()
    private var started = false

    /** The ids of the open tabs; empty until the stored tabs are back. */
    private var live = emptySet<TabId>()

    /** Safe to call again. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            engine.snapshots.collect { snapshot ->
                if (snapshot.id !in live) return@collect snapshot.bitmap.recycle()
                cache.put(snapshot.id, snapshot.bitmap.asImageBitmap())
                versions[snapshot.id] = (versions[snapshot.id] ?: 0) + 1
                scope.launch(io) { compress(snapshot.bitmap)?.let { store.save(snapshot.id.value, it) } }
            }
        }
        scope.launch {
            engine.tabs.collect { state ->
                // Empty means "not restored yet", not "everything closed".
                if (state.tabs.isEmpty()) return@collect
                val ids = snapshotIds(state)
                (live - ids).forEach {
                    cache.remove(it)
                    versions.remove(it)
                    scope.launch(io) { store.delete(it.value) }
                }
                if (live != ids) {
                    live = ids
                    scope.launch(io) { store.sweep(ids.mapTo(HashSet()) { it.value }) }
                }
            }
        }
    }

    /** Takes a picture of the page on screen now (the app is leaving the screen). */
    fun captureNow() {
        scope.launch { engine.captureSelected() }
    }

    /** Read this while composing: it changes when [load] has something new for [id]. */
    fun versionOf(id: TabId): Int = versions[id] ?: 0

    fun cached(id: TabId): ImageBitmap? = cache.get(id)

    /** The memory copy, else the file; null when the tab has no picture. */
    suspend fun load(id: TabId): ImageBitmap? {
        cache.get(id)?.let { return it }
        val bitmap = withContext(io) { store.load(id.value)?.let(::decode) } ?: return null
        if (id !in live) {
            // Closed during the read: do not put its picture back into the cache.
            bitmap.recycle()
            return null
        }
        val image = bitmap.asImageBitmap()
        if (cache.get(id) == null) cache.put(id, image)
        return cache.get(id) ?: image
    }

    /** Null for a file whose picture is over [MAX_DECODE_SIDE] on a side, or is not a picture. */
    private fun decode(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_DECODE_SIDE || bounds.outHeight !in 1..MAX_DECODE_SIDE) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    @Suppress("DEPRECATION")
    private fun compress(bitmap: Bitmap): ByteArray? = try {
        val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Bitmap.CompressFormat.WEBP_LOSSY else Bitmap.CompressFormat.WEBP
        ByteArrayOutputStream().also { bitmap.compress(format, QUALITY, it) }.toByteArray()
    } catch (_: IllegalStateException) {
        null
    }
}

/** The tabs that may have a snapshot: never a private one. */
fun snapshotIds(state: TabsState): Set<TabId> = state.tabs.filterNot { it.isPrivate }.mapTo(HashSet()) { it.id }
