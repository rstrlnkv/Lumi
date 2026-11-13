package ru.digitalpunk.lumi.data.downloads

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun item(id: Long, state: DownloadState, startedAt: Long = 0, url: String = "https://example.org/a.pdf", private: Boolean = false) =
    DownloadItem(id, url, "a.pdf", "application/pdf", null, "example.org", 10, 0, state, startedAt, private)

class DownloadNamesTest {
    private fun clean(raw: String?, mimeExt: String? = null) = sanitizeFileName(raw, "download", mimeExt)

    @Test fun pathTraversalKeepsOnlyTheLastSegment() {
        assertEquals("passwd", clean("../../etc/passwd"))
        assertEquals("x", clean("..\\x"))
        assertEquals("x.txt", clean("C:\\x.txt"))
    }

    @Test fun leadingDotsAreTrimmedSoTheFileIsNotHidden() = assertEquals("hidden", clean(".hidden"))

    @Test fun dotNamesAndEmptyNamesBecomeTheFallback() {
        assertEquals("download", clean("."))
        assertEquals("download", clean(".."))
        assertEquals("download", clean(""))
        assertEquals("download", clean(null))
        assertEquals("download", clean("   "))
    }

    @Test fun controlNulAndReservedCharactersAreRemoved() = assertEquals("abcd.txt", clean("a\u0000b\nc:*?\"<>|d.txt"))

    @Test fun aBidiOverrideIsRemovedSoTheExtensionCannotBeSpoofed() = assertEquals("invoicefdp.exe", clean("invoice\u202Efdp.exe"))

    @Test fun invisibleOnlyNamesBecomeTheFallback() {
        assertEquals("download", clean("\u200B\u200B"))
        assertEquals("download", clean("\u200C"))
    }

    @Test fun aZeroWidthNonJoinerInsideAWordIsKept() = assertEquals("می\u200Cخواهم.txt", clean("می\u200Cخواهم.txt"))

    @Test fun aLongNameIsCutToTheByteLimitAndKeepsItsExtension() {
        val name = clean("a".repeat(300) + ".pdf")
        assertTrue(name.toByteArray().size <= 200)
        assertTrue(name.endsWith(".pdf"))
    }

    @Test fun aCutNeverSplitsASurrogatePair() {
        val name = clean("\uD83D\uDE00".repeat(100) + ".txt")
        assertTrue(name.toByteArray().size <= 200)
        assertTrue(name.removeSuffix(".txt").all { it.isSurrogate() })
        assertEquals(0, name.removeSuffix(".txt").length % 2)
        assertTrue(name.endsWith(".txt"))
    }

    @Test fun aNameOfOnlyAnExtensionLosesItsLeadingDot() = assertEquals("pdf", clean(".pdf"))

    @Test fun theMimeExtensionIsAddedOnlyWhenTheNameHasNone() {
        assertEquals("report.pdf", clean("report", "pdf"))
        assertEquals("report.pdf", clean("report.pdf", "pdf"))
        assertEquals("download.pdf", clean(null, "pdf"))
    }
}

class UniqueNameTest {
    @Test fun countsUpBeforeTheExtension() {
        val taken = mutableSetOf("a.pdf")
        assertEquals("a (1).pdf", uniqueName("a.pdf") { it in taken })
        taken += "a (1).pdf"
        assertEquals("a (2).pdf", uniqueName("a.pdf") { it in taken })
    }

    @Test fun aFreeNameIsKept() = assertEquals("a.pdf", uniqueName("a.pdf") { false })

    @Test fun aNameWithoutADotGetsTheNumberAtTheEnd() = assertEquals("readme (1)", uniqueName("readme") { it == "readme" })

    @Test fun afterTheLimitATimestampSuffixIsUsed() {
        val name = uniqueName("a.pdf") { !it.matches(Regex("a-\\d+\\.pdf")) }
        assertTrue(name.matches(Regex("a-\\d+\\.pdf")), name)
    }
}

class DownloadStateTest {
    @Test fun legalTransitions() {
        assertEquals(DownloadState.Running, next(DownloadState.Queued, DownloadStep.Start))
        assertEquals(DownloadState.Cancelled, next(DownloadState.Queued, DownloadStep.Cancel))
        assertEquals(DownloadState.Failed, next(DownloadState.Queued, DownloadStep.Fail))
        assertEquals(DownloadState.Done, next(DownloadState.Running, DownloadStep.Finish))
        assertEquals(DownloadState.Failed, next(DownloadState.Running, DownloadStep.Fail))
        assertEquals(DownloadState.Cancelled, next(DownloadState.Running, DownloadStep.Cancel))
        assertEquals(DownloadState.Queued, next(DownloadState.Failed, DownloadStep.Retry))
        assertEquals(DownloadState.Queued, next(DownloadState.Cancelled, DownloadStep.Retry))
    }

    @Test fun anInterruptFailsOnlyActiveEntries() {
        assertEquals(DownloadState.Failed, next(DownloadState.Queued, DownloadStep.Interrupt))
        assertEquals(DownloadState.Failed, next(DownloadState.Running, DownloadStep.Interrupt))
        assertEquals(DownloadState.Done, next(DownloadState.Done, DownloadStep.Interrupt))
    }

    @Test fun illegalPairsKeepTheState() {
        assertEquals(DownloadState.Done, next(DownloadState.Done, DownloadStep.Retry))
        assertEquals(DownloadState.Done, next(DownloadState.Done, DownloadStep.Fail))
        assertEquals(DownloadState.Failed, next(DownloadState.Failed, DownloadStep.Finish))
        assertEquals(DownloadState.Queued, next(DownloadState.Queued, DownloadStep.Finish))
    }
}

class DownloadClearTest {
    private val list = DownloadState.entries.mapIndexed { i, s -> item(i.toLong(), s) }

    @Test fun clearKeepsQueuedAndRunning() {
        assertEquals(listOf(2L, 3L, 4L), clearableIds(list))
        assertEquals(listOf(DownloadState.Queued, DownloadState.Running), afterClear(list).map { it.state })
    }
}

class DownloadDaysTest {
    private val zone = ZoneId.of("Europe/Berlin")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = ZonedDateTime.of(y, m, d, h, min, 0, 0, zone).toInstant().toEpochMilli()

    @Test fun newestDayFirstAndNewestEntryFirstWithIdTieBreak() {
        val days = groupDownloadsByDay(
            listOf(item(1, DownloadState.Done, at(2026, 11, 12, 10)), item(2, DownloadState.Done, at(2026, 11, 13, 9)), item(3, DownloadState.Done, at(2026, 11, 13, 9))),
            zone,
        )
        assertEquals(listOf(LocalDate.of(2026, 11, 13), LocalDate.of(2026, 11, 12)), days.map { it.date })
        assertEquals(listOf(3L, 2L), days[0].items.map { it.id })
    }

    @Test fun midnightBelongsToTheNewDay() {
        val days = groupDownloadsByDay(listOf(item(1, DownloadState.Done, at(2026, 11, 13, 0))), zone)
        assertEquals(LocalDate.of(2026, 11, 13), days.single().date)
    }

    @Test fun aDstDayIsOneSection() {
        val days = groupDownloadsByDay(listOf(item(1, DownloadState.Done, at(2026, 10, 25, 1)), item(2, DownloadState.Done, at(2026, 10, 25, 23))), zone)
        assertEquals(1, days.size)
    }
}

class DownloadRetryTest {
    @Test fun onlyHttpAndHttpsUrlsCanBeRetried() {
        assertTrue(canRetry(item(1, DownloadState.Failed)))
        assertFalse(canRetry(item(1, DownloadState.Failed, url = "")))
        assertFalse(canRetry(item(1, DownloadState.Failed, url = "blob:https://x.org/1")))
    }

    @Test fun anOverCapUrlIsStoredEmpty() {
        assertEquals("", storedUrl("https://x.org/" + "a".repeat(3000)))
        assertEquals("https://x.org/a", storedUrl("https://x.org/a"))
    }
}

class MimeForNameTest {
    @Test fun headerMimeWinsUnlessItIsGeneric() {
        assertEquals("application/pdf", mimeForName("a.bin", "application/pdf") { null })
        assertEquals("application/zip", mimeForName("a.zip", "application/octet-stream") { if (it == "zip") "application/zip" else null })
        assertEquals("application/octet-stream", mimeForName("a", null) { null })
    }
}

class FreeSpaceTest {
    @Test fun aDownloadNeedsItsRemainingBytesPlusTheFloor() {
        assertTrue(enoughSpace(free = FREE_SPACE_FLOOR + 10, remaining = 10))
        assertFalse(enoughSpace(free = FREE_SPACE_FLOOR + 9, remaining = 10))
    }

    @Test fun anUnknownSizeOnlyNeedsTheFloor() {
        assertTrue(enoughSpace(free = FREE_SPACE_FLOOR, remaining = -1))
        assertFalse(enoughSpace(free = FREE_SPACE_FLOOR - 1, remaining = -1))
    }
}
