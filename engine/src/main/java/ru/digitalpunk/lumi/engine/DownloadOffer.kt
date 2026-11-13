package ru.digitalpunk.lumi.engine

import java.io.InputStream

/**
 * A file a page sent (or a re-fetch got), with Gecko's response body. Holding it pauses the transfer; the stream must end in
 * exactly one place: [take] hands it to the writer, which closes it, or [discard] closes it.
 */
class DownloadOffer(
    val url: String,
    val suggestedName: String?,
    val mimeType: String?,
    /** -1 when unknown. Gecko decodes `Content-Encoding`, so it may be smaller than what arrives. */
    val contentLength: Long,
    val site: String?,
    val inPrivateTab: Boolean,
    private var body: InputStream?,
) {
    /** The stream, once. Null if already taken or discarded. The caller must close it. */
    @Synchronized fun take(): InputStream? = body.also { body = null }

    /** Closes the stream if nobody took it. Idempotent. */
    @Synchronized fun discard() {
        runCatching { body?.close() }
        body = null
    }
}

/** An offer for an http(s) response, its headers read case-insensitively; anything else (blob:, data:) is refused and its stream closed. */
internal fun downloadOffer(uri: String, headers: Map<String, String>, pageUrl: String, private: Boolean, body: InputStream): DownloadOffer? {
    if (!isDownloadableUrl(uri)) {
        runCatching { body.close() }
        return null
    }
    fun header(name: String) = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
    return DownloadOffer(
        uri,
        parseContentDisposition(header("Content-Disposition")) ?: nameFromUrl(uri),
        header("Content-Type")?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() },
        header("Content-Length")?.trim()?.toLongOrNull()?.takeIf { it >= 0 } ?: -1,
        siteKey(pageUrl) ?: siteKey(uri),
        private,
        body,
    )
}
