package ru.digitalpunk.lumi.data.downloads

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private fun entry(id: Long, state: DownloadState, startedAt: Long = 0, url: String = "https://example.org/a.pdf", private: Boolean = false) =
    DownloadItem(id, url, "a.pdf", "application/pdf", null, "example.org", 10, 0, state, startedAt, private)

class SanitizeEdgeTest {
    private fun clean(raw: String?, mimeExt: String? = null, max: Int = 200) = sanitizeFileName(raw, "download", mimeExt, max)

    @Test fun aSupplementaryCharWhoseLow16BitsAreAReservedCharIsKept() {
        // U+1003A truncates to ':' when narrowed to Char; it is a letter, not a reserved character.
        for (low in ":*?\"<>|") assertEquals("a\uD800${(0xDC00 + low.code).toChar()}b", clean("a\uD800${(0xDC00 + low.code).toChar()}b"), "U+1000${low.code.toString(16)}")
    }

    @Test fun everyReservedAsciiCharacterIsRemoved() {
        for (c in ":*?\"<>|") assertEquals("ab", clean("a${c}b"), "char $c")
    }

    @Test fun bidiIsolatesMarksTagsAndTheBomAreRemoved() {
        for (cp in listOf(0x2066, 0x2067, 0x2068, 0x2069, 0x202A, 0x202B, 0x202C, 0x202D, 0x202E, 0x200E, 0x200F, 0x200B, 0x2060, 0xFEFF, 0xE0041)) {
            assertEquals("ab.txt", clean("a" + String(Character.toChars(cp)) + "b.txt"), "U+${cp.toString(16)}")
        }
    }

    @Test fun lineSeparatorsDelAndC1ControlsAreRemoved() {
        for (cp in listOf(0x7F, 0x85, 0x9F, 0x2028, 0x2029, 0x09, 0x1F)) {
            assertEquals("ab", clean("a" + String(Character.toChars(cp)) + "b"), "U+${cp.toString(16)}")
        }
    }

    @Test fun dotOnlyAndSlashOnlyNamesBecomeTheFallback() {
        for (raw in listOf("...", "a/..", "../", "/", "\\", " . . ", "..\\..\\", "‮.‮")) assertEquals("download", clean(raw), raw)
    }

    @Test fun aTrailingDotOrSpaceIsTrimmedBeforeTheMimeExtensionIsAdded() {
        assertEquals("report.pdf", clean("report. ", "pdf"))
        assertEquals("a b.txt", clean("  a b.txt  "))
    }

    @Test fun anExistingExtensionOfAnyCaseIsNotDoubled() = assertEquals("A.PDF", clean("A.PDF", "pdf"))

    @Test fun theByteLimitIsExactAndLiteral() {
        val exact = "a".repeat(196) + ".pdf"
        assertEquals(200, exact.length)
        assertEquals(exact, clean(exact))
        assertEquals("a".repeat(196) + ".pdf", clean("a".repeat(197) + ".pdf"))
        assertEquals("abc.pdf", clean("abcdefg.pdf", max = 7))
        assertEquals("ab.pdf", clean("abcdefg.pdf", max = 6))
    }

    @Test fun twoByteLettersAreCutOnABoundaryInsideTheByteLimit() {
        val name = clean("é".repeat(300) + ".pdf")
        assertEquals("é".repeat(98) + ".pdf", name)
        assertEquals(200, name.toByteArray().size)
    }

    @Test fun anExtensionOf16CharsIsKeptAndOneOf17IsCut() {
        val ext16 = "x".repeat(16)
        assertEquals("a".repeat(200 - 17) + ".$ext16", clean("a".repeat(300) + ".$ext16"))
        assertEquals("a".repeat(200), clean("a".repeat(300) + ".${"x".repeat(17)}"))
    }

    @Test fun aMultiByteExtensionIsBudgetedInBytesNotChars() {
        val ext = "é".repeat(10)
        val name = clean("a".repeat(300) + ".$ext")
        assertEquals("a".repeat(200 - 21) + ".$ext", name)
        assertEquals(200, name.toByteArray().size)
    }

    @Test fun onlyTheLastExtensionOfADoubleExtensionIsKeptWhenCut() {
        val name = clean("a".repeat(300) + ".tar.gz")
        assertTrue(name.endsWith(".gz"))
        assertTrue(name.toByteArray().size <= 200)
    }

    @Test fun aCutThatEndsInSpacesOrDotsDoesNotLeaveThem() {
        val name = clean("a".repeat(190) + " ".repeat(20) + "tail.pdf")
        assertEquals("a".repeat(190) + ".pdf", name)
    }

    @Test fun aNameThatIsAllInvisibleAfterTheCutKeepsTheFallbackAndExtension() {
        val name = clean(" ".repeat(10) + "x.pdf", max = 5)
        assertTrue(name.endsWith(".pdf"), name)
        assertTrue(name.toByteArray().size <= 5 + "download".length, name)
    }

    @Test fun theResultNeverContainsASeparatorOrControlWhateverComesIn() {
        val nasty = "..\\../a\u0000/b‮:*c\n.. /d.exe"
        val out = clean(nasty, "bin")
        assertEquals("d.exe", out)
        assertTrue(out.none { it == '/' || it == '\\' || it.isISOControl() })
    }
}

class UniqueNameEdgeTest {
    @Test fun theNumber999IsStillUsedAndTheTimestampComesOnlyAfter() {
        assertEquals("a (999).pdf", uniqueName("a.pdf") { it != "a (999).pdf" })
        assertEquals("a (1).pdf", uniqueName("a.pdf") { it == "a.pdf" })
        val stamped = uniqueName("a.pdf") { Regex("a \\(\\d+\\)\\.pdf").matches(it) || it == "a.pdf" }
        assertTrue(Regex("a-\\d{10,}\\.pdf").matches(stamped), stamped)
    }

    @Test fun aDotFileGetsTheNumberAfterTheWholeName() = assertEquals(".bashrc (1)", uniqueName(".bashrc") { it == ".bashrc" })

    @Test fun aDoubleExtensionCountsBeforeTheLastDot() = assertEquals("a.tar (1).gz", uniqueName("a.tar.gz") { it == "a.tar.gz" })

    @Test fun aLeadingDotOnlyNameIsTreatedAsHavingNoExtension() = assertEquals("x (1)", uniqueName("x") { it == "x" })

    @Test fun aTrailingDotNameGetsTheNumberAfterTheDot() {
        val out = uniqueName("a.") { it == "a." }
        assertEquals("a. (1)", out)
    }
}

class DownloadMatrixTest {
    private val s = DownloadState.entries
    private val steps = DownloadStep.entries

    @Test fun everyStateStepPairHasTheDocumentedResult() {
        val q = DownloadState.Queued; val r = DownloadState.Running; val d = DownloadState.Done; val f = DownloadState.Failed; val c = DownloadState.Cancelled
        val expected = mapOf(
            q to mapOf(DownloadStep.Start to r, DownloadStep.Finish to q, DownloadStep.Fail to f, DownloadStep.Cancel to c, DownloadStep.Retry to q, DownloadStep.Interrupt to f),
            r to mapOf(DownloadStep.Start to r, DownloadStep.Finish to d, DownloadStep.Fail to f, DownloadStep.Cancel to c, DownloadStep.Retry to r, DownloadStep.Interrupt to f),
            d to mapOf(DownloadStep.Start to d, DownloadStep.Finish to d, DownloadStep.Fail to d, DownloadStep.Cancel to d, DownloadStep.Retry to d, DownloadStep.Interrupt to d),
            f to mapOf(DownloadStep.Start to f, DownloadStep.Finish to f, DownloadStep.Fail to f, DownloadStep.Cancel to f, DownloadStep.Retry to q, DownloadStep.Interrupt to f),
            c to mapOf(DownloadStep.Start to c, DownloadStep.Finish to c, DownloadStep.Fail to c, DownloadStep.Cancel to c, DownloadStep.Retry to q, DownloadStep.Interrupt to c),
        )
        assertEquals(s.size * steps.size, expected.values.sumOf { it.size })
        for (state in s) for (step in steps) assertEquals(expected.getValue(state).getValue(step), next(state, step), "$state + $step")
    }

    @Test fun downloadAgainIsRetryOfADoneEntryAndIsRefusedByTheMachine() {
        // "Download again" of a Done file is a new entry, not a transition: Done never goes back to Queued.
        assertEquals(DownloadState.Done, next(DownloadState.Done, DownloadStep.Retry))
    }

    @Test fun isActiveIsTrueOnlyForQueuedAndRunning() {
        assertEquals(listOf(true, true, false, false, false), DownloadState.entries.map { it.isActive })
    }
}

class ClearEdgeTest {
    @Test fun clearOfAnEmptyListAndOfAllActiveRemovesNothing() {
        assertEquals(emptyList(), clearableIds(emptyList()))
        val active = listOf(entry(1, DownloadState.Queued), entry(2, DownloadState.Running))
        assertEquals(emptyList(), clearableIds(active))
        assertEquals(active, afterClear(active))
    }

    @Test fun everyFinishedStateIsCleared() {
        val all = listOf(entry(7, DownloadState.Done), entry(8, DownloadState.Failed), entry(9, DownloadState.Cancelled), entry(10, DownloadState.Running))
        assertEquals(listOf(7L, 8L, 9L), clearableIds(all))
        assertEquals(listOf(10L), afterClear(all).map { it.id })
    }
}

class DaysEdgeTest {
    @Test fun anEmptyListHasNoDaysAndTheZoneDecidesTheDay() {
        assertEquals(emptyList(), groupDownloadsByDay(emptyList(), ZoneId.of("UTC")))
        val lateUtc = 1_783_000_000_000L - (1_783_000_000_000L % 86_400_000L) + 23 * 3_600_000L + 30 * 60_000L
        val utc = groupDownloadsByDay(listOf(entry(1, DownloadState.Done, lateUtc)), ZoneId.of("UTC")).single().date
        val tokyo = groupDownloadsByDay(listOf(entry(1, DownloadState.Done, lateUtc)), ZoneId.of("Asia/Tokyo")).single().date
        assertEquals(utc.plusDays(1), tokyo)
    }
}

class RetryAndUrlEdgeTest {
    @Test fun theSchemeCheckIgnoresCaseAndRejectsNearMisses() {
        assertTrue(canRetry(entry(1, DownloadState.Failed, url = "HTTPS://x.org/a")))
        assertTrue(canRetry(entry(1, DownloadState.Failed, url = "Http://x.org/a")))
        for (url in listOf("httpx://x.org", "http:x.org", "ftp://x.org", " https://x.org", "file:///a", "content://x", "https")) assertFalse(canRetry(entry(1, DownloadState.Failed, url = url)), url)
    }

    @Test fun theUrlCapIsExact() {
        val at = "https://x.org/" + "a".repeat(MAX_URL_LENGTH - 14)
        assertEquals(2048, at.length)
        assertEquals(at, storedUrl(at))
        assertEquals("", storedUrl(at + "a"))
        assertEquals("", storedUrl(""))
    }

    @Test fun mimeFallsBackOnABlankHeaderAndOnAMissingOrUnknownExtension() {
        val zip = { e: String -> if (e == "zip") "application/zip" else null }
        assertEquals("application/zip", mimeForName("A.ZIP", "  ", zip))
        assertEquals("application/zip", mimeForName("A.ZIP", "", zip))
        assertEquals(OCTET_STREAM, mimeForName("a.", null, zip))
        assertEquals(OCTET_STREAM, mimeForName("a.xyz", null, zip))
        assertEquals(OCTET_STREAM, mimeForName("zip", null, zip))
        assertEquals("text/plain", mimeForName("a.zip", "text/plain", zip))
    }
}

class StoresEdgeTest {
    @Test fun clearFinishedKeepsActiveRowsAndCountsTheRest() = runBlocking {
        val dao = FakeDownloadDao()
        val repo = DownloadRepository(dao)
        repo.insert(entry(0, DownloadState.Queued)); repo.insert(entry(0, DownloadState.Running))
        repo.insert(entry(0, DownloadState.Done)); repo.insert(entry(0, DownloadState.Failed)); repo.insert(entry(0, DownloadState.Cancelled))
        assertEquals(3, repo.clearFinished())
        assertEquals(listOf("Queued", "Running"), dao.rows.value.map { it.state })
        assertEquals(0, repo.clearFinished())
    }

    @Test fun anUndoneDeleteComesBackWithItsOwnId() = runBlocking {
        val dao = FakeDownloadDao()
        val repo = DownloadRepository(dao)
        val id = repo.insert(entry(0, DownloadState.Done, startedAt = 5))
        val saved = repo.observe().first().single()
        repo.delete(id)
        assertTrue(dao.rows.value.isEmpty())
        assertEquals(id, repo.insert(saved))
        assertEquals(saved, repo.observe().first().single())
    }

    @Test fun recoveringTwiceReturnsNothingTheSecondTime() = runBlocking {
        val repo = DownloadRepository(FakeDownloadDao())
        repo.insert(entry(0, DownloadState.Queued))
        assertEquals(1, repo.recoverInterrupted().size)
        assertEquals(0, repo.recoverInterrupted().size)
    }

    @Test fun updateChangesOnlyThatRow() = runBlocking {
        val dao = FakeDownloadDao()
        val repo = DownloadRepository(dao)
        val a = repo.insert(entry(0, DownloadState.Running))
        repo.insert(entry(0, DownloadState.Running))
        repo.update(entry(a, DownloadState.Done))
        assertEquals(listOf("Done", "Running"), dao.rows.value.map { it.state })
    }

    @Test fun mimeSiteAndUriAreCappedOnWrite() = runBlocking {
        val dao = FakeDownloadDao()
        DownloadRepository(dao).insert(entry(0, DownloadState.Done).copy(mimeType = "m".repeat(500), site = "s".repeat(500), uri = "u".repeat(5000)))
        val row = dao.rows.value.single()
        assertEquals(128, row.mimeType.length)
        assertEquals(256, row.site.length)
        assertEquals(2048, row.uri!!.length)
    }

    @Test fun aPrivateStoreRefusesNothingButForcesThePrivateFlagAndKeepsIdsUnique() {
        val store = PrivateDownloads()
        val a = store.add(entry(5, DownloadState.Running))
        assertTrue(a.isPrivate)
        assertTrue(a.id < 0)
        store.remove(a.id)
        val b = store.add(entry(5, DownloadState.Running))
        assertNotEquals(a.id, b.id)
        assertEquals(listOf(b.id), store.items.value.map { it.id })
    }

    @Test fun restoreIsIdempotentUpdateOfAnUnknownIdIsIgnoredAndDropOnEmptyIsSafe() {
        val store = PrivateDownloads()
        store.dropFinished()
        val a = store.add(entry(0, DownloadState.Done))
        store.restore(a)
        assertEquals(1, store.items.value.size)
        store.remove(a.id)
        store.restore(a)
        assertEquals(listOf(a.id), store.items.value.map { it.id })
        store.update(a.copy(id = -999, state = DownloadState.Failed))
        assertEquals(DownloadState.Done, store.items.value.single().state)
    }

    @Test fun dropFinishedRemovesEveryFinishedState() {
        val store = PrivateDownloads()
        val kept = store.add(entry(0, DownloadState.Queued))
        for (s in listOf(DownloadState.Done, DownloadState.Failed, DownloadState.Cancelled)) store.add(entry(0, s))
        store.dropFinished()
        assertEquals(listOf(kept.id), store.items.value.map { it.id })
        assertEquals(0, store.clearFinished())
    }
}
