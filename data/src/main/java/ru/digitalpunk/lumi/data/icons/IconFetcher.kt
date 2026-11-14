package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

class HttpResponse(val finalUrl: String, val body: ByteArray)

/** One GET without cookies, of at most [maxBytes] (HTML is cut after its head). Null on any error, timeout, non-2xx answer or oversized body. */
interface HttpGet {
    suspend fun get(url: String, accept: String, maxBytes: Int): HttpResponse?
}

/** Image bytes to the stored 192 px PNG, or null when they do not decode or are too small. */
fun interface IconCodec {
    fun encode(bytes: ByteArray): ByteArray?
}

/** Finds a site's icon: the root page's links, its web app manifest when no link is large enough, then `/favicon.ico`. Never throws but for the caller's cancellation. */
class IconFetcher(private val http: HttpGet, private val codec: IconCodec, private val totalTimeoutMs: Long = TOTAL_TIMEOUT_MS) {
    suspend fun fetch(target: IconTarget): ByteArray? = withTimeoutOrNull(totalTimeoutMs) {
        val page = attempt { http.get(target.rootUrl, HTML, HTML_MAX_BYTES) }
        val html = page?.let { String(it.body, Charsets.UTF_8) }
        var links = html?.let { parseIconLinks(it, page.finalUrl) }.orEmpty()
        val manifest = html?.let { manifestLink(it, page.finalUrl) }
        if (manifest != null && needsManifest(links)) {
            links = links + attempt { http.get(manifest, MANIFEST, MANIFEST_MAX_BYTES)?.let { parseManifestIcons(String(it.body, Charsets.UTF_8), it.finalUrl) } }.orEmpty()
        }
        for (candidate in rankIcons(links, target.rootUrl)) {
            attempt { http.get(candidate.url, IMAGE, MAX_BYTES)?.let { codec.encode(it.body) } }?.let { return@withTimeoutOrNull it }
        }
        null
    }

    private inline fun <T> attempt(block: () -> T?): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    internal companion object {
        const val HTML = "text/html"
        const val IMAGE = "image/*"
        const val MANIFEST = "application/manifest+json, application/json;q=0.9, */*;q=0.1"
        const val HTML_MAX_BYTES = 256 * 1024
        const val MANIFEST_MAX_BYTES = 128 * 1024
    }
}
