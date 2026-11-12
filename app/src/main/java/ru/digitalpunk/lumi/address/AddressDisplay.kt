package ru.digitalpunk.lumi.address

data class DisplayUrl(val text: String, val isSecure: Boolean)

private fun isBlank(url: String) = url.equals("about:blank", ignoreCase = true)
private val WEB_SCHEME = Regex("^(https?)://", RegexOption.IGNORE_CASE)

/** What the unfocused address bar shows: the host only, as Safari does. */
fun displayUrl(url: String): DisplayUrl {
    if (isBlank(url)) return DisplayUrl("", false)
    val scheme = WEB_SCHEME.find(url) ?: return DisplayUrl(url, false)
    val authority = url.substring(scheme.range.last + 1).takeWhile { it !in "/?#" }
    val host = authority.substringAfterLast('@').replace(Regex(":\\d*$"), "").replace(Regex("^www\\.", RegexOption.IGNORE_CASE), "")
    if (host.isEmpty()) return DisplayUrl(url, false)
    return DisplayUrl(host, scheme.groupValues[1].equals("https", ignoreCase = true))
}

/** What the address bar offers for editing: the full URL, nothing for the blank page. */
fun editableUrl(url: String): String = if (isBlank(url)) "" else url
