package ru.digitalpunk.lumi.data.library

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HistoryRulesTest {
    private val utc = ZoneId.of("UTC")
    private val berlin = ZoneId.of("Europe/Berlin")
    private val newYork = ZoneId.of("America/New_York")

    private fun at(zone: ZoneId, text: String): Long = LocalDateTime.parse(text).atZone(zone).toInstant().toEpochMilli()
    private fun visit(id: Long, url: String, at: Long, title: String = "") = HistoryEntity(id, url, title, at)

    /** Whether a visit at [visitAt] is removed by a clear that starts at [from] (the repository deletes `visitedAt >= from`). */
    private fun cleared(from: Long?, visitAt: Long) = from == null || visitAt >= from

    @Test fun lastHourIncludesExactlySixtyMinutesAgoButNotOneMillisecondMore() {
        val now = Instant.parse("2026-11-13T12:00:00Z")
        val from = clearFrom(ClearRange.LastHour, now, utc)
        assertTrue(cleared(from, now.toEpochMilli() - 3_600_000))
        assertFalse(cleared(from, now.toEpochMilli() - 3_600_001))
    }

    @Test fun todayStartsAtLocalMidnight() {
        val now = Instant.ofEpochMilli(at(berlin, "2026-11-13T09:30:00"))
        val from = clearFrom(ClearRange.Today, now, berlin)
        assertTrue(cleared(from, at(berlin, "2026-11-13T00:00:00")))
        assertFalse(cleared(from, at(berlin, "2026-11-12T23:59:59.999")))
    }

    @Test fun todayIsRightOnShortAndLongDstDays() {
        // Berlin springs forward on 29 March 2026 (a 23 h day); New York falls back on 1 November 2026 (25 h).
        val spring = clearFrom(ClearRange.Today, Instant.ofEpochMilli(at(berlin, "2026-03-29T20:00:00")), berlin)
        assertEquals(at(berlin, "2026-03-29T00:00:00"), spring)
        val fall = clearFrom(ClearRange.Today, Instant.ofEpochMilli(at(newYork, "2026-11-01T23:30:00")), newYork)
        assertEquals(at(newYork, "2026-11-01T00:00:00"), fall)
        assertEquals(25 * 3_600_000L, at(newYork, "2026-11-02T00:00:00") - fall!!)
    }

    @Test fun allTimeHasNoStart() = assertNull(clearFrom(ClearRange.AllTime, Instant.EPOCH, utc))

    @Test fun theSameUrlOnTheSameDayCollapsesIntoOneRowWithTheLatestTimeAndAllIds() {
        val days = groupByDay(
            listOf(
                visit(1, "https://a.com/", at(utc, "2026-11-13T08:00:00"), "Old"),
                visit(2, "https://a.com/", at(utc, "2026-11-13T10:00:00"), ""),
                visit(3, "https://b.com/", at(utc, "2026-11-13T09:00:00"), "B"),
            ),
            utc,
        )
        assertEquals(1, days.size)
        val (a, b) = days.single().rows
        assertEquals(HistoryRow("https://a.com/", "Old", at(utc, "2026-11-13T10:00:00"), listOf(2L, 1L)), a)
        assertEquals(listOf(3L), b.ids)
    }

    @Test fun theSameUrlOnTwoDaysGivesTwoRowsAndDaysAreNewestFirst() {
        val days = groupByDay(
            listOf(
                visit(1, "https://a.com/", at(utc, "2026-11-12T23:59:00")),
                visit(2, "https://a.com/", at(utc, "2026-11-13T00:01:00")),
            ),
            utc,
        )
        assertEquals(listOf(LocalDate.parse("2026-11-13"), LocalDate.parse("2026-11-12")), days.map { it.date })
        assertEquals(listOf(listOf(2L), listOf(1L)), days.map { d -> d.rows.single().ids })
    }

    @Test fun theDayDependsOnTheZone() {
        val t = Instant.parse("2026-11-13T23:30:00Z").toEpochMilli()
        assertEquals(LocalDate.parse("2026-11-14"), groupByDay(listOf(visit(1, "https://a.com/", t)), berlin).single().date)
    }

    @Test fun dayKindIsTodayYesterdayOrOtherAcrossMonthEnds() {
        val today = LocalDate.parse("2026-12-01")
        assertEquals(DayKind.Today, dayKind(today, today))
        assertEquals(DayKind.Yesterday, dayKind(LocalDate.parse("2026-11-30"), today))
        assertEquals(DayKind.Other, dayKind(LocalDate.parse("2026-11-29"), today))
    }

    @Test fun searchIgnoresCaseInCyrillicAndMatchesTitleOrUrl() {
        val entries = listOf(
            visit(1, "https://news.ru/", 0, "Новости ДНЯ"),
            visit(2, "https://Example.com/Path", 0, "Site"),
            visit(3, "https://other.com/", 0, "Other"),
        )
        assertEquals(listOf(1L), searchHistory(entries, "новости дня").map { it.id })
        assertEquals(listOf(2L), searchHistory(entries, "EXAMPLE.COM/path").map { it.id })
        assertEquals(entries, searchHistory(entries, "  "))
    }

    @Test fun aRegexLookingQueryIsLiteral() {
        val entries = listOf(visit(1, "https://a.com/", 0, "a.b"), visit(2, "https://b.com/", 0, "axb"))
        assertEquals(listOf(1L), searchHistory(entries, "a.b").map { it.id })
        assertEquals(emptyList(), searchHistory(entries, ".*"))
    }

    @Test fun onlyWebPagesOfAtMost2048CharactersAreRecorded() {
        assertTrue(isRecordable("https://example.com/"))
        assertTrue(isRecordable("HTTP://example.com/"))
        listOf("about:blank", "javascript:alert(1)", "file:///sdcard/a.html", "data:text/html,hi", "", "example.com")
            .forEach { assertFalse(isRecordable(it), it) }
        val prefix = "https://a.com/"
        assertTrue(isRecordable(prefix + "x".repeat(2048 - prefix.length)))
        assertFalse(isRecordable(prefix + "x".repeat(2049 - prefix.length)))
    }
}
