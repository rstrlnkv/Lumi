package ru.digitalpunk.lumi.ui.settings

import ru.digitalpunk.lumi.data.library.HistoryEntity
import ru.digitalpunk.lumi.engine.siteKey

/** A site in "Site data": its key ([siteKey]) and the newest visit, in epoch milliseconds. */
data class SiteRow(val host: String, val lastVisit: Long)

/**
 * The sites of the history, one per site key, newest visit first. Gecko cannot list the sites that hold data, so the
 * history stands in for that list. A blank [query] keeps all; otherwise the host must contain it, ignoring case.
 */
fun siteRows(history: List<HistoryEntity>, query: String): List<SiteRow> {
    val q = query.trim()
    val newest = HashMap<String, Long>()
    history.forEach { visit ->
        val host = siteKey(visit.url) ?: return@forEach
        if (visit.visitedAt > (newest[host] ?: Long.MIN_VALUE)) newest[host] = visit.visitedAt
    }
    return newest.filterKeys { q.isEmpty() || it.contains(q, ignoreCase = true) }
        .map { (host, at) -> SiteRow(host, at) }
        .sortedWith(compareByDescending<SiteRow> { it.lastVisit }.thenBy { it.host })
}

/** The sites with tracker protection off, by host. */
fun unprotectedRows(hosts: Set<String>): List<String> = hosts.sorted()
