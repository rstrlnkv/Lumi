package ru.digitalpunk.lumi.data.downloads

enum class DownloadState { Queued, Running, Done, Failed, Cancelled }

/** What happened to a download; [next] turns it into the new state. */
enum class DownloadStep { Start, Finish, Fail, Cancel, Retry, Interrupt }

/** One entry of the Downloads list. A private entry ([isPrivate], negative [id]) lives only in [PrivateDownloads], never in Room. */
data class DownloadItem(
    val id: Long,
    val url: String,
    val fileName: String,
    val mimeType: String,
    /** A MediaStore content URI (API 29+) or an absolute path (API 26-28); null until the file exists. */
    val uri: String?,
    val site: String,
    /** -1 when unknown. */
    val totalBytes: Long,
    val doneBytes: Long,
    val state: DownloadState,
    val startedAt: Long,
    val isPrivate: Boolean = false,
)

fun isPrivateId(id: Long) = id < 0

fun DownloadEntity.toItem() = DownloadItem(
    id, url, fileName, mimeType, uri, site, totalBytes, doneBytes,
    DownloadState.entries.firstOrNull { it.name == state } ?: DownloadState.Failed, startedAt,
)

/** Strings are capped on write: a row over the `CursorWindow` limit would break every read of the list. */
internal fun DownloadItem.toEntity(): DownloadEntity {
    require(!isPrivate) { "A private download is never stored" }
    return DownloadEntity(
        id, storedUrl(url), capUtf8(fileName, MAX_NAME_BYTES), mimeType.take(MAX_MIME_LENGTH), uri?.take(MAX_URL_LENGTH),
        site.take(MAX_SITE_LENGTH), totalBytes, doneBytes, state.name, startedAt,
    )
}

private const val MAX_NAME_BYTES = 255
private const val MAX_MIME_LENGTH = 128
private const val MAX_SITE_LENGTH = 256
