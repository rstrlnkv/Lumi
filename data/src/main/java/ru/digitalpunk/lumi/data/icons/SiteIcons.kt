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

/**
 * A stored icon, decoded. [fillsTile]: full-bleed, clear corners allowed. [iconClass] and [tileMode] decide how it is drawn; [letter] colours the
 * letter tile that stands in for a small icon on the start page.
 */
class SiteIcon(val bitmap: Bitmap, val fillsTile: Boolean, val lightEdge: Boolean, val iconClass: IconClass, val letter: LetterColours)

private const val MAX_CONCURRENT_FETCHES = 3
private const val CACHE_ENTRIES = 48
private const val OFFERS_KEPT = 64

/** Site icons for the process: fetched in the background, stored as files, decoded on demand. */
class SiteIcons(
    private val store: SiteIconStore,
    private val fetch: suspend (IconTarget, PageIconOffer?) -> FetchedIcon?,
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
    /** The last page offer tried per host, so a page that sends the same links again starts no fetch. Guarded by [lock]. */
    private val offered = object : LinkedHashMap<String, PageIconOffer>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PageIconOffer>?) = size > OFFERS_KEPT
    }

    /** The hosts of the person's bookmarks: only their icons are taken from an opened page. */
    @Volatile var wantedHosts: Set<String> = emptySet()

    /** Goes up by one after each icon written, and after a [forget] or [retainOnly] that deleted any. */
    val changes: StateFlow<Int> = changeCount.asStateFlow()

    /**
     * Fetches the icon of [url]'s host unless it is stored, fresh and has current meta, already being fetched, or (without
     * [retryFailed]) failed before in this process. A failure or a worse icon keeps the old one.
     */
    fun request(url: String, retryFailed: Boolean = true) {
        val target = iconTarget(url) ?: return
        synchronized(lock) {
            if (target.host in inFlight || (!retryFailed && target.host in failed)) return
            inFlight += target.host
        }
        start(target, null) { meta, modified -> modified != null && !isStale(modified, now()) && meta != null }
    }

    /**
     * Tries the icon links of a page the person opened in a normal tab, for a bookmarked host only, unless its stored icon is
     * already sharp and fresh or the same links were tried in this process. Each URL passes [isAllowedIconUrl] again.
     */
    fun offerPageLinks(offer: PageIconOffer) {
        val target = iconTarget(offer.pageUrl) ?: return
        if (target.host !in wantedHosts) return
        val clean = offer.copy(candidates = offer.candidates.filter { isAllowedIconUrl(it.url) }, manifestUrl = offer.manifestUrl?.takeIf(::isAllowedIconUrl))
        synchronized(lock) {
            if (target.host in inFlight || offered[target.host] == clean) return
            inFlight += target.host
            offered[target.host] = clean
        }
        start(target, clean) { meta, modified -> modified != null && !isStale(modified, now()) && iconClass(meta?.quality) == IconClass.Sharp }
    }

    /** Fetches unless [skip] says the stored icon will do; writes the result only when it is not worse than the stored one. */
    private fun start(target: IconTarget, offer: PageIconOffer?, skip: (IconMeta?, Long?) -> Boolean) {
        val host = target.host
        scope.launch(io) {
            val stored = store.meta(host)
            if (skip(stored, store.lastModified(host))) return@launch
            val got = try {
                permits.withPermit { fetch(target, offer) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                log("Site icon fetch failed", e)
                null
            }
            val written = got != null && synchronized(files) {
                when {
                    synchronized(lock) { host in dropped } -> false
                    !shouldReplace(stored?.quality, got.meta.quality) -> { store.touch(host, now()); false }
                    else -> IconMeta.insert(got.png, got.meta)?.let { store.write(host, it) } == true
                }
            }
            synchronized(lock) {
                if (got != null) failed -= host
                if (written) cache.remove(host) else if (got == null && host !in dropped) failed += host
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
        val icon = withContext(io) { store.read(host)?.let { decode(host, it) } } ?: return null
        synchronized(lock) { cache[host] = icon }
        return icon
    }

    private fun decode(host: String, png: ByteArray): SiteIcon? {
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val meta = IconMeta.parse(png)
        val letter = letterColours(tileColour(dominantColour(pixels), meta?.themeColor, host))
        return SiteIcon(bitmap, fillsTile(pixels, bitmap.width), hasLightEdge(pixels, bitmap.width), iconClass(meta?.quality), letter)
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
