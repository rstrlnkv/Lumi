package ru.digitalpunk.lumi.downloads

import ru.digitalpunk.lumi.data.downloads.DownloadItem
import ru.digitalpunk.lumi.data.downloads.DownloadState
import ru.digitalpunk.lumi.data.downloads.canRetry

private const val SEPARATOR = " · "

/** 0-100, or null when the total is unknown or already passed (Gecko may report the compressed size). */
fun percent(done: Long, total: Long): Int? = if (total <= 0 || done > total) null else (done.coerceAtLeast(0) * 100 / total).toInt()

/** TalkBack hears tens of percent, so a running download does not talk on every update. */
fun spokenPercent(percent: Int): Int = (percent + 5) / 10 * 10

/** "1.2 GB of 5.8 GB", or only the done size when the total is unknown. [of] fills the localised "%1$s of %2$s". */
fun progressLine(done: Long, total: Long, size: (Long) -> String, of: (String, String) -> String): String =
    if (total > 0 && done <= total) of(size(done), size(total)) else size(done)

/** The localised words of a row's supporting line. */
class LineWords(val of: (String, String) -> String, val failed: String, val cancelled: String, val private: String, val gone: String)

/** The second line of a Downloads row (spec 4.1). [missing]: a finished file is no longer there. */
fun supportingLine(item: DownloadItem, size: (Long) -> String, time: String, missing: Boolean, words: LineWords): String {
    // A site may be blank (an IPv6 host, an about: page): it then leaves no separator behind.
    val parts = when (item.state) {
        DownloadState.Queued, DownloadState.Running -> listOf(progressLine(item.doneBytes, item.totalBytes, size, words.of), item.site)
        DownloadState.Done -> listOf(if (missing) words.gone else size(item.doneBytes), item.site, time)
        DownloadState.Failed -> listOf(words.failed, item.site, time)
        DownloadState.Cancelled -> listOf(words.cancelled, item.site, time)
    }
    val line = parts.filter { it.isNotBlank() }.joinToString(SEPARATOR)
    return if (item.isPrivate) words.private + SEPARATOR + line else line
}

/** The ask sheet's detail line: [size] alone when there is no site, else [join] fills the localised "%1$s · %2$s". */
fun sizeAndSite(size: String, site: String?, join: (String, String) -> String): String = if (site.isNullOrBlank()) size else join(size, site)

/** A private download's snackbar never shows over the private lock screen. */
fun showsDownloadSnackbar(item: DownloadItem, lockShown: Boolean): Boolean = !(item.isPrivate && lockShown)

enum class FileKind { Pdf, Image, Video, Audio, Archive, DiskImage, Apk, Other }

/** The file-type icon (spec 3.1): the MIME type first, then the extension. */
fun fileKind(mime: String?, name: String): FileKind {
    val type = mime?.lowercase().orEmpty()
    return when {
        type == "application/pdf" -> FileKind.Pdf
        type.startsWith("image/") -> FileKind.Image
        type.startsWith("video/") -> FileKind.Video
        type.startsWith("audio/") -> FileKind.Audio
        type == "application/vnd.android.package-archive" -> FileKind.Apk
        else -> when (name.substringAfterLast('.', "").lowercase()) {
            "pdf" -> FileKind.Pdf
            "zip", "rar", "7z", "tar", "gz" -> FileKind.Archive
            "iso", "img", "dmg" -> FileKind.DiskImage
            "apk" -> FileKind.Apk
            else -> FileKind.Other
        }
    }
}

/** Compose has no middle ellipsis: a long name keeps its extension, as in `annual-report-2…pdf`. */
fun ellipsizeKeepingExtension(name: String, max: Int): String {
    if (name.length <= max) return name
    val dot = name.lastIndexOf('.')
    val tail = "…" + if (dot > 0 && name.length - dot - 1 in 1..16) name.substring(dot + 1) else ""
    val head = name.take((max - tail.length).coerceAtLeast(1)).let { if (it.last().isHighSurrogate()) it.dropLast(1) else it }
    return head + tail
}

/** A private download never shows its name in a notification: the lock screen may show it. */
fun notificationTitle(item: DownloadItem, privateTitle: String): String = if (item.isPrivate) privateTitle else item.fileName

/** Retry from a notification: normal downloads with a stored web address only. */
fun notificationCanRetry(item: DownloadItem): Boolean = !item.isPrivate && canRetry(item)

/** Lets one progress update through per [minIntervalMs]; a clock that goes back starts over. */
class ProgressGate(private val minIntervalMs: Long) {
    private var last: Long? = null

    fun pass(now: Long): Boolean {
        val previous = last
        if (previous != null && now >= previous && now - previous < minIntervalMs) return false
        last = now
        return true
    }
}
