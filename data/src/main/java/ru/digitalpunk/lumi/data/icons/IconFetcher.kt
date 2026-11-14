package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

class HttpResponse(val finalUrl: String, val body: ByteArray)

/** One GET without cookies, as [userAgent], of at most [maxBytes] (HTML is cut after its head). Null on any error, timeout, non-2xx answer or oversized body. */
interface HttpGet {
    suspend fun get(url: String, accept: String, maxBytes: Int, userAgent: String): HttpResponse?
}

/** The stored 192 px PNG of an icon and what it was made from. */
class EncodedIcon(val png: ByteArray, val quality: IconQuality)

/** Image bytes to the stored 192 px PNG, or null when they do not decode or are too small. */
fun interface IconCodec {
    fun encode(bytes: ByteArray): EncodedIcon?
}

/** The best icon found: its PNG (without meta) and the [meta] to store with it. */
class FetchedIcon(val png: ByteArray, val meta: IconMeta)

/**
 * Finds a site's best icon: the links of the opened page ([PageIconOffer]) and, when none of them is large enough, of the
 * root page; the web app manifest when still no link is large enough; the root touch-icon paths, an SVG, then `/favicon.ico`
 * (see [rankIcons]). Stops at the first sharp icon, else keeps the best. Never throws but for the caller's cancellation.
 * Every request names Safari on an iPhone ([SAFARI_UA]): Google and YouTube list their touch icons only to it.
 */
class IconFetcher(private val http: HttpGet, private val codec: IconCodec, private val totalTimeoutMs: Long = TOTAL_TIMEOUT_MS) {
    suspend fun fetch(target: IconTarget, offer: PageIconOffer? = null): FetchedIcon? {
        var best: EncodedIcon? = null
        withTimeoutOrNull(totalTimeoutMs) {
            var links = offer?.candidates.orEmpty()
            var manifest = offer?.manifestUrl
            if (needsManifest(links)) {
                val page = attempt { http.get(target.rootUrl, HTML, HTML_MAX_BYTES, SAFARI_UA) }
                val html = page?.let { String(it.body, Charsets.UTF_8) }
                links = links + html?.let { parseIconLinks(it, page.finalUrl) }.orEmpty()
                manifest = manifest ?: html?.let { manifestLink(it, page.finalUrl) }
            }
            if (manifest != null && needsManifest(links)) {
                links = links + attempt { http.get(manifest, MANIFEST, MANIFEST_MAX_BYTES, SAFARI_UA)?.let { parseManifestIcons(String(it.body, Charsets.UTF_8), it.finalUrl) } }.orEmpty()
            }
            for (candidate in rankIcons(links, target.rootUrl)) {
                val have = iconClass(best?.quality)
                if (have == IconClass.Sharp) break
                // An SVG or a favicon cannot beat what is already found.
                if ((candidate.svg || candidate.kind == IconCandidate.Kind.Favicon) && have >= IconClass.Vector) continue
                val got = attempt { http.get(candidate.url, IMAGE, MAX_BYTES, SAFARI_UA)?.let { codec.encode(it.body) } } ?: continue
                if (best?.let { !shouldReplace(got.quality, it.quality) } != false) best = got
            }
        }
        return best?.let { FetchedIcon(it.png, IconMeta(it.quality.srcPx, it.quality.vector, offer?.themeColor)) }
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
        const val SAFARI_UA = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"
    }
}
