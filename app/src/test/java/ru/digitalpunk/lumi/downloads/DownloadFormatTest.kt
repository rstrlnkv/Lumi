package ru.digitalpunk.lumi.downloads

import ru.digitalpunk.lumi.data.downloads.DownloadItem
import ru.digitalpunk.lumi.data.downloads.DownloadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun item(state: DownloadState, done: Long = 0, total: Long = 100, private: Boolean = false, name: String = "a.pdf") =
    DownloadItem(1, "https://x.org/a.pdf", name, "application/pdf", null, "x.org", total, done, state, 1, private)

private val words = LineWords(of = { a, b -> "$a of $b" }, failed = "Failed", cancelled = "Cancelled", private = "Private", gone = "File deleted")
private val size: (Long) -> String = { "$it B" }

class DownloadPercentTest {
    @Test fun percentNeedsAKnownTotalThatIsNotPassed() {
        assertEquals(50, percent(50, 100))
        assertEquals(100, percent(100, 100))
        assertNull(percent(1, 0))
        assertNull(percent(1, -1))
        assertNull(percent(200, 100))
        assertEquals(0, percent(-5, 100))
    }

    @Test fun talkBackHearsTensOfPercent() {
        assertEquals(40, spokenPercent(44))
        assertEquals(50, spokenPercent(45))
        assertEquals(100, spokenPercent(100))
    }

    @Test fun progressLineShowsTheTotalOnlyWhenKnown() {
        assertEquals("5 B of 10 B", progressLine(5, 10, size, words.of))
        assertEquals("5 B", progressLine(5, -1, size, words.of))
        assertEquals("50 B", progressLine(50, 10, size, words.of))
    }
}

class SupportingLineTest {
    @Test fun eachStateHasItsLine() {
        assertEquals("5 B of 100 B · x.org", supportingLine(item(DownloadState.Running, 5), size, "12:24", false, words))
        assertEquals("100 B · x.org · 12:24", supportingLine(item(DownloadState.Done, 100), size, "12:24", false, words))
        assertEquals("File deleted · x.org · 12:24", supportingLine(item(DownloadState.Done, 100), size, "12:24", true, words))
        assertEquals("Failed · x.org · 12:24", supportingLine(item(DownloadState.Failed), size, "12:24", false, words))
        assertEquals("Cancelled · x.org · 12:24", supportingLine(item(DownloadState.Cancelled), size, "12:24", false, words))
    }

    @Test fun aPrivateEntryIsPrefixed() =
        assertEquals("Private · Failed · x.org · 12:24", supportingLine(item(DownloadState.Failed, private = true), size, "12:24", false, words))
}

class FileKindTest {
    @Test fun mimeComesFirst() {
        assertEquals(FileKind.Pdf, fileKind("application/pdf", "a.zip"))
        assertEquals(FileKind.Image, fileKind("image/png", "a"))
        assertEquals(FileKind.Video, fileKind("video/mp4", "a"))
        assertEquals(FileKind.Audio, fileKind("audio/ogg", "a"))
        assertEquals(FileKind.Apk, fileKind("application/vnd.android.package-archive", "a"))
    }

    @Test fun theExtensionDecidesForAGenericMime() {
        listOf("zip", "rar", "7z", "tar", "GZ").forEach { assertEquals(FileKind.Archive, fileKind("application/octet-stream", "a.$it"), it) }
        listOf("iso", "img", "dmg").forEach { assertEquals(FileKind.DiskImage, fileKind(null, "a.$it"), it) }
        assertEquals(FileKind.Pdf, fileKind(null, "A.PDF"))
        assertEquals(FileKind.Other, fileKind(null, "readme"))
    }
}

class EllipsizeKeepingExtensionTest {
    @Test fun aShortNameIsKept() = assertEquals("a.pdf", ellipsizeKeepingExtension("a.pdf", 20))

    @Test fun aLongNameKeepsItsExtension() {
        val out = ellipsizeKeepingExtension("annual-report-2026-final.pdf", 20)
        assertTrue(out.endsWith("…pdf"), out)
        assertEquals(20, out.length)
    }

    @Test fun aLongNameWithoutExtensionEndsWithAnEllipsis() = assertEquals("abcd…", ellipsizeKeepingExtension("abcdefghij", 5))
}

class NotificationTextTest {
    @Test fun aPrivateDownloadNeverShowsItsName() {
        assertEquals("Private download", notificationTitle(item(DownloadState.Running, private = true, name = "secret.pdf"), "Private download"))
        assertEquals("secret.pdf", notificationTitle(item(DownloadState.Running, name = "secret.pdf"), "Private download"))
    }

    @Test fun onlyNormalRetryableEntriesOfferRetry() {
        assertTrue(notificationCanRetry(item(DownloadState.Failed)))
        assertFalse(notificationCanRetry(item(DownloadState.Failed, private = true)))
    }
}

class ProgressGateTest {
    @Test fun passesOncePerIntervalAndSurvivesAClockGoingBack() {
        val gate = ProgressGate(250)
        assertTrue(gate.pass(1000))
        assertFalse(gate.pass(1100))
        assertTrue(gate.pass(1250))
        assertTrue(gate.pass(10))
        assertFalse(gate.pass(20))
    }
}
