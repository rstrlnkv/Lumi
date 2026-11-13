package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withTimeoutOrNull

class HttpResponse(val finalUrl: String, val body: ByteArray)

/** One GET. Null on any error, timeout, non-200 answer or oversized body. */
interface HttpGet {
    suspend fun get(url: String, accept: String): HttpResponse?
}

/** Image bytes to the stored 96 px PNG, or null when they do not decode or are too small. */
fun interface IconCodec {
    fun encode(bytes: ByteArray): ByteArray?
}

/** Finds a site's icon: the root page's links, then `/favicon.ico`. Never throws but for the caller's cancellation. */
class IconFetcher(private val http: HttpGet, private val codec: IconCodec, private val totalTimeoutMs: Long = TOTAL_TIMEOUT_MS) {
    suspend fun fetch(target: IconTarget): ByteArray? = withTimeoutOrNull(totalTimeoutMs) {
        val page = attempt { http.get(target.rootUrl, HTML) }
        val links = page?.let { parseIconLinks(String(it.body, Charsets.UTF_8), it.finalUrl) }.orEmpty()
        for (candidate in orderCandidates(links, target.rootUrl)) {
            attempt { http.get(candidate.url, IMAGE)?.let { codec.encode(it.body) } }?.let { return@withTimeoutOrNull it }
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
    }
}
