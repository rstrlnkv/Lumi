package ru.digitalpunk.lumi.data.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File

/** A stored icon, decoded. [fillsTile]: fully opaque, drawn edge to edge; otherwise drawn small and centred. */
class SiteIcon(val bitmap: Bitmap, val fillsTile: Boolean, val lightEdge: Boolean)

private const val MAX_CONCURRENT_FETCHES = 3
private const val CACHE_ENTRIES = 48

/** Site icons for the process: fetched in the background, stored as files, decoded on demand. */
class SiteIcons(
    private val store: SiteIconStore,
    private val fetch: suspend (IconTarget) -> ByteArray?,
    private val scope: CoroutineScope,
    private val io: CoroutineDispatcher,
    private val now: () -> Long,
    private val log: (String, Throwable) -> Unit,
) : IconRetention {
    private val lock = Any()
    private val inFlight = HashSet<String>()
    private val failed = HashSet<String>()
    /** In-flight hosts whose icon was deleted meanwhile: their fetch result is dropped. Guarded by [lock]. */
    private val dropped = HashSet<String>()
    /** Held around each file write and delete, so a dropped mark and the file change never interleave. */
    private val files = Any()
    private val permits = Semaphore(MAX_CONCURRENT_FETCHES)
    private val cache = object : LinkedHashMap<String, SiteIcon>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SiteIcon>?) = size > CACHE_ENTRIES
    }
    private val changeCount = MutableStateFlow(0)

    /** Goes up by one after each icon written, and after a [forget] or [retainOnly] that deleted any. */
    val changes: StateFlow<Int> = changeCount.asStateFlow()

    /**
     * Fetches the icon of [url]'s host unless it is stored, fresh and 192 px, already being fetched, or (without [retryFailed])
     * failed before in this process. A failure keeps the old icon.
     */
    fun request(url: String, retryFailed: Boolean = true) {
        val target = iconTarget(url) ?: return
        val host = target.host
        synchronized(lock) {
            if (host in inFlight || (!retryFailed && host in failed)) return
            inFlight += host
        }
        scope.launch(io) {
            val modified = store.lastModified(host)
            if (modified != null && !isStale(modified, now()) && store.isCurrent(host)) return@launch
            val png = try {
                permits.withPermit { fetch(target) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Site icon fetch failed", e)
                null
            }
            val written = png != null && synchronized(files) { synchronized(lock) { host !in dropped } && store.write(host, png) }
            synchronized(lock) {
                if (written) { failed -= host; cache.remove(host) } else if (host !in dropped) failed += host
            }
            if (written) changeCount.update { it + 1 }
        }.invokeOnCompletion { synchronized(lock) { inFlight -= host; dropped -= host } }
    }

    /** Deletes the icons of [hosts], also one still being fetched, and lets a later request fetch them again. Blocking file IO. */
    override fun forget(hosts: Set<String>) {
        val deleted = synchronized(files) {
            synchronized(lock) {
                hosts.forEach { cache.remove(it) }
                failed -= hosts
                dropped += inFlight.intersect(hosts)
            }
            hosts.count(store::delete)
        }
        if (deleted > 0) changeCount.update { it + 1 }
    }

    /** Deletes every icon but those of [hosts], also one still being fetched. Blocking file IO. */
    override fun retainOnly(hosts: Set<String>) {
        val deleted = synchronized(files) {
            synchronized(lock) {
                cache.keys.retainAll(hosts)
                failed.retainAll(hosts)
                dropped += inFlight - hosts
            }
            store.retainOnly(hosts)
        }
        if (deleted > 0) changeCount.update { it + 1 }
    }

    /** The decoded icon if it is in memory; no IO. */
    fun peek(host: String): SiteIcon? = synchronized(lock) { cache[host] }

    /** The stored icon of [host], decoded off the main thread; null when none is stored. */
    suspend fun load(host: String): SiteIcon? {
        peek(host)?.let { return it }
        val icon = withContext(io) { store.read(host)?.let(::decode) } ?: return null
        synchronized(lock) { cache[host] = icon }
        return icon
    }

    private fun decode(png: ByteArray): SiteIcon? {
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return SiteIcon(bitmap, fillsTile(pixels), hasLightEdge(pixels, bitmap.width))
    }

    companion object {
        fun create(context: Context, scope: CoroutineScope, http: HttpGet) = SiteIcons(
            SiteIconStore(File(context.filesDir, "icons")),
            IconFetcher(http, BitmapIconCodec())::fetch,
            scope,
            Dispatchers.IO,
            System::currentTimeMillis,
        ) { message, error -> Log.w("SiteIcons", message, error) }
    }
}
