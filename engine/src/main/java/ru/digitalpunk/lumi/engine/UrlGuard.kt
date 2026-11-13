package ru.digitalpunk.lumi.engine

/** True for web pages and the allowed `about:` pages; `javascript:`, `file:`, `about:config` and the rest are refused. */
internal fun isLoadableUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://") ||
        lower == "about:blank" || lower == "about:crashcontent"
}

/** Only web responses become downloads; `blob:`, `data:`, `file:` and the rest are refused. */
fun isDownloadableUrl(url: String): Boolean = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
