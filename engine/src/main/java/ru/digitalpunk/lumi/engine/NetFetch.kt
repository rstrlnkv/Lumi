package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoWebExecutor
import java.io.ByteArrayOutputStream
import java.io.InputStream

/** A body fetched through Gecko and the URL it came from after redirects. */
class FetchedBytes(val finalUrl: String, val body: ByteArray)

/** Never cookies or credentials (ADR 014); a private tab's request also runs in Gecko's private context. */
internal fun fetchFlags(private: Boolean): Int =
    GeckoWebExecutor.FETCH_FLAGS_ANONYMOUS or (if (private) GeckoWebExecutor.FETCH_FLAGS_PRIVATE else GeckoWebExecutor.FETCH_FLAGS_NONE)

/**
 * Reads at most [max] bytes. Without [htmlHead] a body over [max] gives null; with it the body is cut at [max] and the read
 * stops once `</head>` or `<body` has been read.
 */
internal fun readCapped(input: InputStream, max: Int, htmlHead: Boolean): ByteArray? {
    val limit = if (htmlHead) max else max + 1
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var tail = ""
    while (out.size() < limit) {
        val n = input.read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
        if (htmlHead) {
            val text = tail + String(buf, 0, n, Charsets.ISO_8859_1).lowercase()
            if ("</head>" in text || "<body" in text) break
            tail = text.takeLast(6)
        }
    }
    return if (!htmlHead && out.size() > max) null else out.toByteArray()
}
