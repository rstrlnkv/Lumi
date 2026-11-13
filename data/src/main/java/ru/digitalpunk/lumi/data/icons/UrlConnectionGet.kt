package ru.digitalpunk.lumi.data.icons

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * A plain GET outside GeckoView (ADR 008): https only on every hop, at most [MAX_REDIRECTS] redirects, no cookies
 * (no `CookieHandler` is installed), no Referer, 10 s per connect and read, at most [MAX_BYTES] read.
 */
class UrlConnectionGet : HttpGet {
    override suspend fun get(url: String, accept: String): HttpResponse? {
        if (!isAllowedIconUrl(url)) return null
        var current = url
        for (hop in 0..MAX_REDIRECTS) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = HTTP_TIMEOUT_MS
                readTimeout = HTTP_TIMEOUT_MS
                useCaches = false
                setRequestProperty("User-Agent", "Lumi")
                setRequestProperty("Accept", accept)
            }
            val result = disconnectOnCancel(conn) {
                try {
                    val code = conn.responseCode
                    when {
                        code in 300..399 -> Hop(conn.getHeaderField("Location"))
                        code != 200 || conn.contentLengthLong > MAX_BYTES -> null
                        else -> {
                            val mode = if (accept == IconFetcher.HTML) ReadMode.Html else ReadMode.Image
                            conn.inputStream.use { readBounded(it, MAX_BYTES, mode) }?.let { HttpResponse(current, it) }
                        }
                    }
                } catch (_: IOException) {
                    null
                } finally {
                    conn.disconnect()
                }
            }
            when (result) {
                is HttpResponse -> return result
                is Hop -> current = result.location?.let { resolveRedirect(current, it, hop + 1) } ?: return null
                else -> return null
            }
        }
        return null
    }

    private class Hop(val location: String?)

    /** A blocking socket read ignores cancellation; disconnecting the connection unblocks it. */
    private suspend fun <T> disconnectOnCancel(conn: HttpURLConnection, block: () -> T): T = coroutineScope {
        val work = async(Dispatchers.IO) { block() }
        try {
            work.await()
        } catch (e: CancellationException) {
            conn.disconnect()
            throw e
        }
    }
}
