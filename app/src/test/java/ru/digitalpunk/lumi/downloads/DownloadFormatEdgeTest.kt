package ru.digitalpunk.lumi.downloads

import ru.digitalpunk.lumi.data.downloads.DownloadItem
import ru.digitalpunk.lumi.data.downloads.DownloadState
import ru.digitalpunk.lumi.data.downloads.sanitizeFileName
import ru.digitalpunk.lumi.engine.nameFromUrl
import ru.digitalpunk.lumi.engine.parseContentDisposition
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun entry(state: DownloadState, done: Long = 0, total: Long = 100, private: Boolean = false, name: String = "a.pdf", site: String = "x.org", url: String = "https://x.org/a.pdf", id: Long = 1) =
    DownloadItem(id, url, name, "application/pdf", null, site, total, done, state, 1, private)

private val words = LineWords(of = { a, b -> "$a of $b" }, failed = "Failed", cancelled = "Cancelled", private = "Private", gone = "File deleted")
private val bytes: (Long) -> String = { "$it B" }

class FormatEdgeTest {
    @Test fun percentBoundaries() {
        assertEquals(0, percent(0, 100))
        assertEquals(33, percent(1, 3))
        assertEquals(99, percent(99, 100))
        assertEquals(100, percent(1, 1))
        assertNull(percent(0, 0))
        assertNull(percent(101, 100))
    }

    @Test fun spokenPercentRoundsToTheNearestTen() {
        assertEquals(0, spokenPercent(0))
        assertEquals(0, spokenPercent(4))
        assertEquals(10, spokenPercent(5))
        assertEquals(90, spokenPercent(94))
        assertEquals(100, spokenPercent(95))
    }

    @Test fun progressLineHandlesUnknownZeroAndFullSizes() {
        assertEquals("0 B", progressLine(0, -1, bytes, words.of))
        assertEquals("0 B", progressLine(0, 0, bytes, words.of))
        assertEquals("0 B of 10 B", progressLine(0, 10, bytes, words.of))
        assertEquals("10 B of 10 B", progressLine(10, 10, bytes, words.of))
        assertEquals("11 B", progressLine(11, 10, bytes, words.of))
    }

    @Test fun aQueuedAndAFinishedLineAreNotTheSame() {
        assertEquals("0 B of 100 B · x.org", supportingLine(entry(DownloadState.Queued), bytes, "t", false, words))
        assertEquals("7 B · x.org", supportingLine(entry(DownloadState.Running, 7, -1), bytes, "t", false, words))
        assertEquals("Private · 100 B · x.org · t", supportingLine(entry(DownloadState.Done, 100, private = true), bytes, "t", false, words))
    }

    @Test fun aBlankSiteLeavesNoDanglingSeparator() {
        val running = supportingLine(entry(DownloadState.Running, 5, site = ""), bytes, "t", false, words)
        val done = supportingLine(entry(DownloadState.Done, 100, site = ""), bytes, "t", false, words)
        val failed = supportingLine(entry(DownloadState.Failed, site = ""), bytes, "t", false, words)
        assertEquals("5 B of 100 B", running)
        assertEquals("100 B · t", done)
        assertEquals("Failed · t", failed)
    }

    @Test fun notificationTextForEveryStateOfAPrivateAndANormalDownload() {
        for (state in DownloadState.entries) {
            assertEquals("Private download", notificationTitle(entry(state, private = true, name = "secret.pdf"), "Private download"), "$state")
            assertEquals("secret.pdf", notificationTitle(entry(state, name = "secret.pdf"), "Private download"), "$state")
            assertFalse(notificationCanRetry(entry(state, private = true)), "$state")
        }
        assertFalse(notificationCanRetry(entry(DownloadState.Failed, url = "")))
        assertFalse(notificationCanRetry(entry(DownloadState.Failed, url = "blob:https://x.org/1")))
    }

    @Test fun aPrivateTitleLeaksNeitherTheNameNorTheSite() {
        val title = notificationTitle(entry(DownloadState.Running, private = true, name = "secret.pdf", site = "bank.example"), "Private download")
        assertFalse("secret" in title || "bank" in title)
    }

    @Test fun notificationIdsOfAPrivateAndANormalDownloadNeverMeet() {
        // A private id is the negative of a Room id range: -1 and 1 must not share a notification or a PendingIntent request code.
        for (id in 1L..50L) assertNotEquals(DownloadNotifier.idFor(id), DownloadNotifier.idFor(-id), "id $id")
        assertNotEquals(DownloadNotifier.SUMMARY_ID, DownloadNotifier.idFor(0))
        assertTrue((-1L downTo -50L).none { DownloadNotifier.idFor(it) == DownloadNotifier.SUMMARY_ID })
    }

    @Test fun everyRealNotificationIdStaysClearOfTheSummaryAndKeepsItsSign() {
        // Room ids 1.. sit at 1000 and up, private ids -1.. at -1000 and down: no id may land on SUMMARY_ID (1), and the two ranges never overlap.
        assertEquals(1, DownloadNotifier.SUMMARY_ID)
        for (id in 1L..2000L) {
            assertTrue(DownloadNotifier.idFor(id) >= 1000, "room id $id")
            assertTrue(DownloadNotifier.idFor(-id) <= -1000, "private id $id")
        }
    }

    @Test fun theAskSheetDetailJoinsTheSiteOnlyWhenThereIsOne() {
        val join: (String, String) -> String = { a, b -> "$a · $b" }
        assertEquals("2 MB · x.org", sizeAndSite("2 MB", "x.org", join))
        assertEquals("Size unknown · x.org", sizeAndSite("Size unknown", "x.org", join))
        assertEquals("2 MB", sizeAndSite("2 MB", null, join))
        assertEquals("2 MB", sizeAndSite("2 MB", " ", join))
    }

    @Test fun aPrivateDownloadSnackbarNeverShowsOverTheLockScreen() {
        assertFalse(showsDownloadSnackbar(entry(DownloadState.Done, private = true), lockShown = true))
        assertTrue(showsDownloadSnackbar(entry(DownloadState.Done, private = true), lockShown = false))
        assertTrue(showsDownloadSnackbar(entry(DownloadState.Done), lockShown = true))
    }

    @Test fun progressGateDropsAnUpdateAtTheSameInstantAndRejectedOnesDoNotMoveTheWindow() {
        val gate = ProgressGate(250)
        assertTrue(gate.pass(1000))
        assertFalse(gate.pass(1000))
        assertFalse(gate.pass(1249))
        assertTrue(gate.pass(1250))
    }

    @Test fun anEmojiNameCutNeverLeavesALoneSurrogate() {
        val out = ellipsizeKeepingExtension("\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00.txt", 4)
        assertEquals("…txt", out)
        assertEquals("\uD83D\uDE00…txt", ellipsizeKeepingExtension("\uD83D\uDE00\uD83D\uDE00\uD83D\uDE00.txt", 6))
    }

    @Test fun fileKindOfAMimeWithParametersOrUpperCase() {
        assertEquals(FileKind.Pdf, fileKind("Application/PDF", "x"))
        assertEquals(FileKind.Image, fileKind("IMAGE/PNG", "x"))
        assertEquals(FileKind.Other, fileKind("", ""))
        assertEquals(FileKind.Other, fileKind(null, "pdf"))
    }
}

class HostileNameFlowTest {
    private fun saved(header: String?, url: String, mimeExt: String? = null) =
        sanitizeFileName(parseContentDisposition(header) ?: nameFromUrl(url), "download", mimeExt)

    @Test fun anEncodedTraversalInAHeaderOrAUrlKeepsOnlyTheLastSegment() {
        assertEquals("passwd", saved("attachment; filename*=UTF-8''..%2F..%2Fetc%2Fpasswd", "https://x.org/a"))
        assertEquals("passwd", saved(null, "https://x.org/%2e%2e%2f%2e%2e%2fetc%2fpasswd"))
        assertEquals("x.exe", saved("attachment; filename=\"..\\..\\x.exe\"", "https://x.org/a"))
        assertEquals("evil", saved(null, "https://x.org/a%5C..%5Cevil"))
    }

    @Test fun anEncodedBidiOverrideAndANulInAHeaderAreRemoved() {
        assertEquals("invoicefdp.exe", saved("attachment; filename*=UTF-8''invoice%E2%80%AEfdp.exe", "https://x.org/a"))
        assertEquals("ab.txt", saved("attachment; filename*=UTF-8''a%00b.txt", "https://x.org/a"))
    }

    @Test fun anEmptyOrDotNameInTheHeaderFallsBackWithoutLosingTheMimeExtension() {
        assertEquals("download.pdf", saved("attachment; filename=\"\"", "https://x.org/", "pdf"))
        assertEquals("download.pdf", saved("attachment; filename=\"..\"", "https://x.org/", "pdf"))
        assertEquals("download", saved(null, "https://x.org"))
        assertEquals("download", saved(null, "https://x.org/%00"))
    }

    @Test fun aBrokenPercentSequenceDoesNotCrash() {
        sanitizeFileName(nameFromUrl("https://x.org/%E0%A4%A"), "download")
        sanitizeFileName(nameFromUrl("https://x.org/%zz%"), "download")
        sanitizeFileName(parseContentDisposition("attachment; filename*=UTF-8''%E0%A4%A"), "download")
    }
}

class RussianPluralsTest {
    private val ru = File("src/main/res/values-ru/strings.xml").readText()
    private val en = File("src/main/res/values/strings.xml").readText()
    private fun plurals(xml: String) = Regex("""<plurals name="(\w+)">(.*?)</plurals>""", RegexOption.DOT_MATCHES_ALL).findAll(xml)
        .associate { m -> m.groupValues[1] to Regex("""<item quantity="(\w+)">(.*?)</item>""").findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] } }

    @Test fun everyRussianPluralHasAllFourFormsAndAKeptPlaceholder() {
        val found = plurals(ru)
        assertTrue(found.keys.containsAll(listOf("downloads_active", "downloads_cleared", "download_percent")), found.keys.toString())
        for ((name, forms) in found) {
            assertEquals(setOf("one", "few", "many", "other"), forms.keys, name)
            forms.forEach { (q, text) -> assertTrue(Regex("%(\\d\\$)?d").containsMatchIn(text), "$name/$q has no number: $text") }
        }
    }

    @Test fun everyEnglishDownloadPluralIsInRussianToo() {
        val enNames = plurals(en).keys.filter { "download" in it }
        assertTrue(enNames.isNotEmpty())
        assertTrue(plurals(ru).keys.containsAll(enNames), "missing in RU: ${enNames - plurals(ru).keys}")
    }

    @Test fun theRussianFormsAgreeWithTheirNumbers() {
        val active = plurals(ru).getValue("downloads_active")
        assertTrue(active.getValue("one").endsWith("загрузка"))
        assertTrue(active.getValue("few").endsWith("загрузки"))
        assertTrue(active.getValue("many").endsWith("загрузок"))
        val cleared = plurals(ru).getValue("downloads_cleared")
        assertEquals("Удалена %d запись", cleared.getValue("one"))
        assertEquals("Удалены %d записи", cleared.getValue("few"))
        assertEquals("Удалено %d записей", cleared.getValue("many"))
        val percent = plurals(ru).getValue("download_percent")
        assertTrue(percent.getValue("one").endsWith("%2\$d процент"))
        assertTrue(percent.getValue("few").endsWith("%2\$d процента"))
        assertTrue(percent.getValue("many").endsWith("%2\$d процентов"))
    }
}
