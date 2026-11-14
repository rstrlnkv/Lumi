package ru.digitalpunk.lumi.engine

/** True for web pages and the allowed `about:` pages; `javascript:`, `file:`, `about:config` and the rest are refused. */
internal fun isLoadableUrl(url: String): Boolean {
    val lower = url.lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://") ||
        lower == "about:blank" || lower == "about:crashcontent"
}

/** Only web responses become downloads; `blob:`, `data:`, `file:` and the rest are refused. */
fun isDownloadableUrl(url: String): Boolean = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)

/** A navigation to another app that the selected tab asked for; [url] is an [appLink]. */
data class AppLink(val tab: TabId, val url: String)

private val SCHEME = Regex("^([A-Za-z][A-Za-z0-9+.-]*):")

/** Schemes Gecko loads or refuses by itself; any other scheme belongs to an app (`intent:`, `tel:`, a bank's own). */
private val GECKO_SCHEMES = setOf(
    "http", "https", "about", "data", "blob", "javascript", "file", "view-source", "moz-extension", "resource", "chrome",
    "ws", "wss", "ftp",
)

/** True when [url] has a scheme Gecko does not load, so it must never reach Gecko. */
internal fun isAppScheme(url: String): Boolean =
    SCHEME.find(url.trim())?.groupValues?.get(1)?.lowercase()?.let { it !in GECKO_SCHEMES } ?: false

/** The trimmed [url] when it is an app link short enough to offer ([MAX_LINK_LENGTH]); else null. */
internal fun appLink(url: String): String? = url.trim().takeIf { isAppScheme(it) && it.length <= MAX_LINK_LENGTH }
