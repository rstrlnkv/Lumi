package ru.digitalpunk.lumi.suggest

import ru.digitalpunk.lumi.data.library.SuggestPage

/** Bookmarks and history held in memory for one edit of the address bar, with each page's bare host computed once. */
class LocalIndex(pages: List<SuggestPage>) {
    internal class Entry(val page: SuggestPage, val host: String, val bare: String)

    internal val entries = pages.map { Entry(it, hostOf(it.url), bare(it.url)) }

    /** Hosts for inline completion, best first: most history visits, then a bookmark, then by name. */
    val hosts: List<String> = entries.filter { it.host.isNotEmpty() }.groupBy { it.host }.entries
        .sortedWith(
            compareByDescending<Map.Entry<String, List<Entry>>> { e -> e.value.sumOf { it.page.visits } }
                .thenBy { e -> e.value.none { it.page.bookmark } }
                .thenBy { it.key },
        )
        .map { it.key }

    companion object {
        val Empty = LocalIndex(emptyList())
    }
}

/**
 * At most [limit] bookmarks and history pages for [typed]: a host that starts with it, then a title word that starts with
 * it, then an address that contains it. Inside a tier a bookmark comes first, then more visits, then the newest visit.
 * One row per page, ignoring scheme and `www.`; the row of [pinHost] (the inline completion's host) comes first.
 */
fun rankLocal(index: LocalIndex, typed: String, pinHost: String?, limit: Int = 3): List<Suggestion> {
    val q = typed.trim()
    if (q.isEmpty()) return emptyList()
    val hostQ = if (q.startsWith("www.", ignoreCase = true)) q.drop(4) else q
    val hits = index.entries.mapNotNull { e -> tier(e, q, hostQ)?.let { it to e } }.sortedWith(
        compareBy<Pair<Int, LocalIndex.Entry>> { it.first }
            .thenBy { !it.second.page.bookmark }
            .thenByDescending { it.second.page.visits }
            .thenByDescending { it.second.page.lastVisit }
            .thenBy { it.second.page.url },
    )
    val seen = HashSet<String>()
    val rows = hits.map { it.second }.filterTo(ArrayList()) { seen.add(it.bare.trimEnd('/').lowercase()) }
    val pinned = if (pinHost == null) -1 else rows.indexOfFirst { it.host.equals(pinHost, ignoreCase = true) }
    if (pinned > 0) rows.add(0, rows.removeAt(pinned))
    return rows.take(limit).map { if (it.page.bookmark) Suggestion.Bookmark(it.page.title, it.page.url) else Suggestion.History(it.page.title, it.page.url) }
}

private fun tier(e: LocalIndex.Entry, q: String, hostQ: String): Int? {
    if (hostQ.isNotEmpty() && e.host.startsWith(hostQ, ignoreCase = true)) return 0
    val title = e.page.title
    for (i in title.indices) {
        if ((i == 0 || !title[i - 1].isLetterOrDigit()) && title.startsWith(q, i, ignoreCase = true)) return 1
    }
    return if (e.bare.contains(q, ignoreCase = true)) 2 else null
}

/** The host of an http(s) [url] in lower case, without user info, port, trailing dot or `www.`; empty for other URLs. */
internal fun hostOf(url: String): String {
    if (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true)) return ""
    val authority = url.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore('#').substringAfterLast('@')
    val host = if (authority.startsWith("[")) authority.substringBefore(']') + "]" else authority.substringBefore(':')
    return host.lowercase().trimEnd('.').removePrefix("www.")
}

private fun bare(url: String): String = url.substringAfter("://").let { if (it.startsWith("www.", ignoreCase = true)) it.drop(4) else it }
