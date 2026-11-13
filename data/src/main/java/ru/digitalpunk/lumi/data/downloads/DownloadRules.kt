package ru.digitalpunk.lumi.data.downloads

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

const val MAX_URL_LENGTH = 2048
private const val MAX_EXTENSION_LENGTH = 16
private const val UNIQUE_LIMIT = 999
private const val RESERVED = ":*?\"<>|"
private const val ZWNJ = 0x200C

fun next(state: DownloadState, step: DownloadStep): DownloadState = when {
    step == DownloadStep.Interrupt -> if (state.isActive) DownloadState.Failed else state
    state == DownloadState.Queued -> when (step) {
        DownloadStep.Start -> DownloadState.Running
        DownloadStep.Cancel -> DownloadState.Cancelled
        DownloadStep.Fail -> DownloadState.Failed
        else -> state
    }
    state == DownloadState.Running -> when (step) {
        DownloadStep.Finish -> DownloadState.Done
        DownloadStep.Fail -> DownloadState.Failed
        DownloadStep.Cancel -> DownloadState.Cancelled
        else -> state
    }
    state == DownloadState.Failed || state == DownloadState.Cancelled -> if (step == DownloadStep.Retry) DownloadState.Queued else state
    else -> state
}

val DownloadState.isActive get() = this == DownloadState.Queued || this == DownloadState.Running

/**
 * A file name that is safe to save: the name is a hostile string from a page or a server. Keeps only the last path segment,
 * drops control, format (except the ZWNJ that Persian and Arabic words need), separator and reserved characters, trims
 * spaces and dots (no hidden file), adds [extensionFromMime] when there is no extension and cuts to [maxBytes] of UTF-8,
 * keeping the extension. Empty or invisible names become [fallback].
 */
fun sanitizeFileName(raw: String?, fallback: String, extensionFromMime: String? = null, maxBytes: Int = 200): String {
    val segment = raw.orEmpty().substringAfterLast('/').substringAfterLast('\\')
    val kept = buildString {
        segment.codePoints().forEach { cp ->
            val type = Character.getType(cp)
            val drop = type == Character.CONTROL.toInt() || (type == Character.FORMAT.toInt() && cp != ZWNJ) ||
                type == Character.LINE_SEPARATOR.toInt() || type == Character.PARAGRAPH_SEPARATOR.toInt() || cp.toChar() in RESERVED && cp < 0x80
            if (!drop) appendCodePoint(cp)
        }
    }
    var name = kept.trimName()
    if (name.isEmpty()) name = fallback
    if (extensionFromMime != null && extensionOf(name) == null) name = "$name.$extensionFromMime"
    if (name.toByteArray().size <= maxBytes) return name
    val ext = extensionOf(name)?.takeIf { it.length <= MAX_EXTENSION_LENGTH }?.let { ".$it" }.orEmpty()
    val base = capUtf8(name.removeSuffix(ext), maxBytes - ext.toByteArray().size).trimName()
    return base.ifEmpty { fallback } + ext
}

private fun String.trimName() = trim { it.isWhitespace() || it == '.' || it.code == ZWNJ }

private fun extensionOf(name: String): String? {
    val dot = name.lastIndexOf('.')
    return if (dot > 0 && dot < name.length - 1) name.substring(dot + 1) else null
}

/** [text] cut to at most [maxBytes] of UTF-8, at a code-point boundary. */
internal fun capUtf8(text: String, maxBytes: Int): String {
    var bytes = 0
    var end = 0
    while (end < text.length) {
        val cp = text.codePointAt(end)
        val size = String(Character.toChars(cp)).toByteArray().size
        if (bytes + size > maxBytes) break
        bytes += size
        end += Character.charCount(cp)
    }
    return text.substring(0, end)
}

/** "name (1).ext", "name (2).ext" … up to 999, then a time suffix. API 26-28 only; MediaStore does this itself on 29+. */
fun uniqueName(name: String, exists: (String) -> Boolean): String {
    if (!exists(name)) return name
    val ext = extensionOf(name)?.let { ".$it" }.orEmpty()
    val base = name.removeSuffix(ext)
    for (n in 1..UNIQUE_LIMIT) "$base ($n)$ext".let { if (!exists(it)) return it }
    return "$base-${System.currentTimeMillis()}$ext"
}

/** The ids "Clear list" removes: every entry that is not in progress. Files are never touched. */
fun clearableIds(items: List<DownloadItem>): List<Long> = items.filter { !it.state.isActive }.map { it.id }

fun afterClear(items: List<DownloadItem>): List<DownloadItem> = items.filter { it.state.isActive }

data class DownloadDay(val date: LocalDate, val items: List<DownloadItem>)

/** Days newest first; entries newest first by start, then by id. */
fun groupDownloadsByDay(items: List<DownloadItem>, zone: ZoneId): List<DownloadDay> =
    items.sortedWith(compareByDescending<DownloadItem> { it.startedAt }.thenByDescending { it.id })
        .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .map { (date, list) -> DownloadDay(date, list) }

/** A URL over the cap is stored empty: such an entry has no Retry. */
fun storedUrl(url: String): String = if (url.length > MAX_URL_LENGTH) "" else url

fun canRetry(item: DownloadItem): Boolean = item.url.startsWith("http://", ignoreCase = true) || item.url.startsWith("https://", ignoreCase = true)

/** The header type unless it is blank or generic, then the type for the name's extension ([lookup]), else `application/octet-stream`. */
fun mimeForName(name: String, headerMime: String?, lookup: (String) -> String?): String =
    headerMime?.takeIf { it.isNotBlank() && it != OCTET_STREAM }
        ?: name.substringAfterLast('.', "").lowercase().takeIf { it.isNotEmpty() }?.let(lookup)
        ?: OCTET_STREAM

const val OCTET_STREAM = "application/octet-stream"

/**
 * The name and type of an entry made before its response (Save image, Retry): a [name] without an extension takes one
 * from [headerMime] through [extensionFor]; a name with one keeps it, and a generic [entryMime] gives way to a specific
 * [headerMime]. Otherwise [name] and [entryMime] stay.
 */
fun nameAndMimeAfterResponse(
    name: String, entryMime: String, headerMime: String?, extensionFor: (String) -> String?, lookup: (String) -> String?,
): Pair<String, String> {
    val header = headerMime?.takeIf { it.isNotBlank() && it != OCTET_STREAM } ?: return name to entryMime
    if (extensionOf(name) != null) return name to if (entryMime == OCTET_STREAM) header else entryMime
    val named = sanitizeFileName(name, name, extensionFor(header) ?: return name to entryMime)
    return named to mimeForName(named, header, lookup)
}

/** Whether a response type is an image: Save image keeps nothing else, so a page cannot drop an .apk or .html into Downloads. */
fun isImageType(mime: String?): Boolean = mime?.startsWith("image/", ignoreCase = true) == true

/** Free space a download always leaves on the device, so a file of unknown size cannot fill the disk. */
const val FREE_SPACE_FLOOR = 100L * 1024 * 1024

/** Whether [free] bytes hold the [remaining] bytes (-1 when unknown) and still leave [FREE_SPACE_FLOOR]. */
fun enoughSpace(free: Long, remaining: Long): Boolean = free - remaining.coerceAtLeast(0) >= FREE_SPACE_FLOOR
