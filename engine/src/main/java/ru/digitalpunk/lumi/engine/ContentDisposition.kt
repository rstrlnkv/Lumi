package ru.digitalpunk.lumi.engine

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

private const val MAX_HEADER_LENGTH = 4096
private val SCHEME_AND_AUTHORITY = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/?#]*")

/**
 * The file name a `Content-Disposition` header suggests: `filename*=charset''…` (percent-decoded) wins over `filename=`
 * (quoted with `\"` escapes, or a bare token). Null for no name, an empty one or an overlong header. Not sanitised.
 */
fun parseContentDisposition(header: String?): String? {
    if (header == null || header.length > MAX_HEADER_LENGTH) return null
    val params = dispositionParams(header)
    val starred = params["filename*"]?.let(::decodeExtValue)
    return (starred ?: params["filename"])?.takeIf { it.isNotEmpty() }
}

/** The last path segment of [url], percent-decoded as UTF-8; null for no segment or invalid UTF-8. */
fun nameFromUrl(url: String): String? {
    val path = url.replaceFirst(SCHEME_AND_AUTHORITY, "").substringBefore('#').substringBefore('?')
    return path.substringAfterLast('/').takeIf { it.isNotEmpty() }?.let { percentDecode(it, Charsets.UTF_8) }
}

private fun dispositionParams(header: String): Map<String, String> {
    val out = HashMap<String, String>()
    var i = header.indexOf(';').takeIf { it >= 0 }?.plus(1) ?: return out
    while (i < header.length) {
        val eq = header.indexOf('=', i)
        if (eq < 0) break
        val semi = header.indexOf(';', i)
        if (semi in 0 until eq) { i = semi + 1; continue }
        val key = header.substring(i, eq).trim().lowercase()
        i = eq + 1
        while (i < header.length && header[i] == ' ') i++
        val value: String
        if (i < header.length && header[i] == '"') {
            val quoted = StringBuilder()
            i++
            while (i < header.length && header[i] != '"') {
                if (header[i] == '\\' && i + 1 < header.length) i++
                quoted.append(header[i])
                i++
            }
            value = quoted.toString()
            i = header.indexOf(';', i).let { if (it < 0) header.length else it + 1 }
        } else {
            val end = header.indexOf(';', i).let { if (it < 0) header.length else it }
            value = header.substring(i, end).trim()
            i = end + 1
        }
        out.putIfAbsent(key, value)
    }
    return out
}

/** RFC 8187 `charset'language'value`. */
private fun decodeExtValue(value: String): String? {
    val parts = value.split('\'', limit = 3)
    if (parts.size < 3) return null
    val charset = runCatching { Charset.forName(parts[0]) }.getOrNull() ?: return null
    return percentDecode(parts[2], charset)
}

/** Strict: a broken escape or bytes that are not valid in [charset] give null. */
private fun percentDecode(text: String, charset: Charset): String? {
    val bytes = ByteArrayOutputStream()
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '%') {
            val byte = text.substring(i + 1, minOf(i + 3, text.length)).takeIf { it.length == 2 }?.toIntOrNull(16) ?: return null
            bytes.write(byte)
            i += 3
        } else {
            bytes.write(c.toString().toByteArray(Charsets.UTF_8))
            i++
        }
    }
    return try {
        charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes.toByteArray())).toString()
    } catch (_: CharacterCodingException) {
        null
    }
}
