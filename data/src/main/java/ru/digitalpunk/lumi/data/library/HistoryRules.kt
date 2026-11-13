package ru.digitalpunk.lumi.data.library

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** How long a visit is kept; older ones are pruned on start. */
const val HISTORY_KEEP_DAYS = 90L

/** The longest URL that is stored, in history or as a bookmark. */
internal const val MAX_URL_LENGTH = 2048

/** Titles and folder names are cut to this length when stored, so no row outgrows a cursor window. */
internal const val MAX_TITLE_LENGTH = 256

enum class ClearRange { LastHour, Today, AllTime }

/** The first moment a clear removes (`visitedAt >= it`); null removes everything. "Today" starts at local midnight. */
fun clearFrom(range: ClearRange, now: Instant, zone: ZoneId): Long? = when (range) {
    ClearRange.LastHour -> now.toEpochMilli() - 3_600_000
    ClearRange.Today -> now.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    ClearRange.AllTime -> null
}

/** Visits to one URL on one local day: the latest time, the newest non-empty title, and every visit id. */
data class HistoryRow(val url: String, val title: String, val visitedAt: Long, val ids: List<Long>)

data class HistoryDay(val date: LocalDate, val rows: List<HistoryRow>)

enum class DayKind { Today, Yesterday, Other }

/** Days newest first; rows newest first; the same URL on the same day is one row. */
fun groupByDay(entries: List<HistoryEntity>, zone: ZoneId): List<HistoryDay> =
    entries.sortedWith(compareByDescending<HistoryEntity> { it.visitedAt }.thenByDescending { it.id })
        .groupBy { Instant.ofEpochMilli(it.visitedAt).atZone(zone).toLocalDate() }
        .map { (date, visits) ->
            HistoryDay(
                date,
                visits.groupBy { it.url }.map { (url, same) ->
                    HistoryRow(url, same.firstOrNull { it.title.isNotEmpty() }?.title.orEmpty(), same.first().visitedAt, same.map { it.id })
                },
            )
        }

fun dayKind(date: LocalDate, today: LocalDate): DayKind = when (date) {
    today -> DayKind.Today
    today.minusDays(1) -> DayKind.Yesterday
    else -> DayKind.Other
}

/** Visits whose title or URL contains [query], ignoring case (Cyrillic too); a blank query keeps all. */
fun searchHistory(entries: List<HistoryEntity>, query: String): List<HistoryEntity> {
    val q = query.trim()
    if (q.isEmpty()) return entries
    return entries.filter { it.title.contains(q, ignoreCase = true) || it.url.contains(q, ignoreCase = true) }
}

/** Only web pages are recorded, and only up to 2048 characters. */
fun isRecordable(url: String): Boolean {
    if (url.length > MAX_URL_LENGTH) return false
    val lower = url.lowercase()
    return lower.startsWith("http://") || lower.startsWith("https://")
}
