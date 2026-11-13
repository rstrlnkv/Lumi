package ru.digitalpunk.lumi.engine

/** One entry of a tab's session history as Gecko reports it. */
data class HistoryEntry(val title: String?, val url: String)

/** A row of the Back history menu; [index] is its place in the session history, for `gotoHistoryIndex`. */
data class HistoryRow(val index: Int, val title: String, val url: String, val isCurrent: Boolean)

/** The tab's back history: rows oldest first, the current page last. Kept in memory only, never saved. */
data class BackHistory(val rows: List<HistoryRow> = emptyList()) {
    val hasBack: Boolean get() = rows.size > 1

    fun canGoTo(index: Int): Boolean = rows.any { it.index == index && !it.isCurrent }
}

/** The entries up to [currentIndex]; forward entries are dropped. An index outside [items] gives no rows. */
fun backHistory(items: List<HistoryEntry>, currentIndex: Int): BackHistory {
    if (currentIndex !in items.indices) return BackHistory()
    return BackHistory(items.take(currentIndex + 1).mapIndexed { i, it -> HistoryRow(i, it.title.orEmpty(), it.url, i == currentIndex) })
}

/** The history menu shows at most this many rows, the current one included; more scroll. */
const val HISTORY_VISIBLE_ROWS = 8

fun visibleHistoryRows(rowCount: Int): Int = minOf(rowCount, HISTORY_VISIBLE_ROWS)
