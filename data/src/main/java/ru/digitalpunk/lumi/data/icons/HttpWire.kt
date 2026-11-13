package ru.digitalpunk.lumi.data.icons

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

private const val MAX_HEAD_BYTES = 16 * 1024
private const val MAX_HEADER_LINES = 100
private const val MAX_CHUNK_LINE = 1024

/** A response's status and headers; header names are lower case. */
internal class Head(val status: Int, val headers: Map<String, String>)

/** A plain HTTP/1.1 GET: no cookies, no Referer, no compression, one request per connection. */
internal fun buildRequest(host: String, pathAndQuery: String, accept: String): ByteArray {
    require(pathAndQuery.none { it == ' ' || it == '\r' || it == '\n' }) { "Bad request target" }
    return ("GET $pathAndQuery HTTP/1.1\r\nHost: $host\r\nUser-Agent: Lumi\r\nAccept: $accept\r\n" +
        "Accept-Encoding: identity\r\nConnection: close\r\n\r\n").toByteArray(Charsets.ISO_8859_1)
}

/** Reads the status line and headers up to the blank line, in one capped pass; null for anything but a sane HTTP/1.x head. */
internal fun readHead(input: InputStream): Head? {
    val bytes = ByteArrayOutputStream()
    var last4 = 0
    while (last4 != 0x0D0A0D0A) {
        if (bytes.size() >= MAX_HEAD_BYTES) return null
        val b = input.read()
        if (b < 0) return null
        bytes.write(b)
        last4 = (last4 shl 8) or b
    }
    val lines = bytes.toString(Charsets.ISO_8859_1.name()).removeSuffix("\r\n\r\n").split("\r\n")
    if (lines.size - 1 > MAX_HEADER_LINES) return null
    val status = lines[0].takeIf { it.startsWith("HTTP/1.") }?.split(' ')?.getOrNull(1)
        ?.takeIf { it.length == 3 }?.toIntOrNull() ?: return null
    val headers = HashMap<String, String>()
    for (line in lines.drop(1)) {
        val colon = line.indexOf(':').takeIf { it > 0 } ?: return null
        headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
    }
    return Head(status, headers)
}

/**
 * The body after [head]: chunked, cut at `Content-Length`, or up to the end of the stream. Null for a compressed body,
 * an unknown transfer coding, or a `Content-Length` that is invalid or over [MAX_BYTES].
 */
internal fun bodyStream(head: Head, input: InputStream): InputStream? {
    head.headers["content-encoding"]?.let { if (!it.equals("identity", ignoreCase = true)) return null }
    head.headers["transfer-encoding"]?.let { return if (it.equals("chunked", ignoreCase = true)) ChunkedInputStream(input) else null }
    val length = head.headers["content-length"] ?: return input
    val n = length.toLongOrNull()?.takeIf { it in 0..MAX_BYTES } ?: return null
    return LimitedInputStream(input, n)
}

private class LimitedInputStream(private val input: InputStream, private var left: Long) : InputStream() {
    override fun read(): Int = if (left <= 0) -1 else input.read().also { if (it >= 0) left-- }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (left <= 0) return -1
        return input.read(b, off, minOf(len.toLong(), left).toInt()).also { if (it > 0) left -= it }
    }
}

/** Decodes `Transfer-Encoding: chunked`; extensions and trailers are skipped; bad framing throws [IOException]. */
internal class ChunkedInputStream(private val input: InputStream) : InputStream() {
    private var left = 0L
    private var done = false
    private var first = true

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (done) return -1
        if (left == 0L) {
            if (!first && line().isNotEmpty()) throw IOException("No CRLF after a chunk")
            first = false
            val size = line().substringBefore(';').trim()
            if (size.isEmpty() || size.length > 8 || size.any { Character.digit(it, 16) < 0 }) throw IOException("Bad chunk size")
            left = size.toLong(16)
            if (left == 0L) {
                var trailers = 0
                while (line().isNotEmpty()) if (++trailers > MAX_HEADER_LINES) throw IOException("Too many trailers")
                done = true
                return -1
            }
        }
        val n = input.read(b, off, minOf(len.toLong(), left).toInt())
        if (n < 0) throw IOException("Body ended inside a chunk")
        left -= n
        return n
    }

    /** One CRLF-terminated line of at most [MAX_CHUNK_LINE] characters, without the CRLF. */
    private fun line(): String {
        val out = StringBuilder()
        while (true) {
            val c = input.read()
            if (c < 0) throw IOException("Body ended inside a chunk line")
            if (c == '\n'.code) {
                if (out.isEmpty() || out.last() != '\r') throw IOException("Bare LF in a chunk line")
                return out.substring(0, out.length - 1)
            }
            if (out.length >= MAX_CHUNK_LINE) throw IOException("Chunk line too long")
            out.append(c.toChar())
        }
    }
}
