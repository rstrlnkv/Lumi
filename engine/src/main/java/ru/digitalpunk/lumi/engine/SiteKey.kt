package ru.digitalpunk.lumi.engine

private val WEB_AUTHORITY = Regex("^https?://([^/?#]*)", RegexOption.IGNORE_CASE)
private val PORT = Regex(":\\d*$")

/**
 * The site a per-site setting is stored under: the lower-case host without user info, port, a trailing dot and a leading `www.`.
 * Null for anything but an http(s) URL with a host; IPv6 literals are null for now.
 */
fun siteKey(url: String): String? {
    val authority = WEB_AUTHORITY.find(url)?.groupValues?.get(1) ?: return null
    val host = authority.substringAfterLast('@').replace(PORT, "").lowercase().removePrefix("www.").removeSuffix(".")
    return host.takeIf { it.isNotEmpty() && '[' !in it && ':' !in it }
}
