package ru.digitalpunk.lumi.data.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inputs the first tests did not feed: twice in a row, stale ids, other zones, other scripts. */
class LibraryEdgeCasesTest {
    private val dao = FakeLibraryDao()
    private var clock = 1_000_000_000_000L
    private var counter = 0
    private val repo = LibraryRepository(dao, Dispatchers.Unconfined, now = { clock }, newId = { "id${++counter}" })

    private fun order(parent: String) = children(dao.nodeRows.value, parent).map { it.id }

    @Test fun deletingTheSameBookmarkTwiceReturnsNullTheSecondTime() = runBlocking<Unit> {
        val b = repo.addBookmark(ROOT, "B", "https://b.com/")!!
        assertNotNull(repo.delete(b))
        assertNull(repo.delete(b))
    }

    @Test fun pressingUndoTwiceDoesNotDuplicateTheRestoredRows() = runBlocking<Unit> {
        val f = repo.createFolder(ROOT, "F")!!
        repo.addBookmark(f, "B", "https://b.com/")
        val removed = repo.delete(f)!!
        repo.restore(removed)
        repo.restore(removed)
        assertEquals(2, dao.nodeRows.value.size)
        assertEquals(removed.toSet(), dao.nodeRows.value.toSet())
    }

    @Test fun deletingAMiddleBookmarkKeepsTheOrderAndTheNextOneGoesLast() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        val b = repo.addBookmark(ROOT, "B", "https://b.com/")!!
        val c = repo.addBookmark(ROOT, "C", "https://c.com/")!!
        repo.delete(b)
        val d = repo.addBookmark(ROOT, "D", "https://d.com/")!!
        assertEquals(listOf(a, c, d), order(ROOT))
    }

    @Test fun undoOfTheLastBookmarkAfterANewOneWasAddedStillKeepsBothInCreationOrder() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        clock += 10
        val b = repo.addBookmark(ROOT, "B", "https://b.com/")!!
        val removed = repo.delete(b)!!
        clock += 10
        val c = repo.addBookmark(ROOT, "C", "https://c.com/")!!
        repo.restore(removed)
        assertEquals(listOf(a, b, c), order(ROOT))
    }

    @Test fun movingOutOfAFolderLeavesTheRestInOrder() = runBlocking<Unit> {
        val f = repo.createFolder(ROOT, "F")!!
        val x = repo.addBookmark(f, "X", "https://x.com/")!!
        val y = repo.addBookmark(f, "Y", "https://y.com/")!!
        val z = repo.addBookmark(f, "Z", "https://z.com/")!!
        assertTrue(repo.moveNode(y, ROOT))
        assertEquals(listOf(x, z), order(f))
        assertEquals(y, order(ROOT).last())
    }

    @Test fun movingToTheCurrentFolderChangesNothing() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        val b = repo.addBookmark(ROOT, "B", "https://b.com/")!!
        val before = dao.nodeRows.value.toSet()
        assertTrue(repo.moveNode(a, ROOT))
        assertEquals(before, dao.nodeRows.value.toSet())
        assertEquals(listOf(a, b), order(ROOT))
    }

    @Test fun staleIdsAreRefusedWithoutChangingAnything() = runBlocking<Unit> {
        val f = repo.createFolder(ROOT, "F")!!
        val b = repo.addBookmark(ROOT, "B", "https://b.com/")!!
        val before = dao.nodeRows.value.toSet()
        assertFalse(repo.moveNode("gone", ROOT))
        assertFalse(repo.moveNode(b, "gone"))
        assertFalse(repo.moveNode(f, b))
        assertFalse(repo.updateBookmark(b, "B", "https://b.com/", "gone"))
        assertFalse(repo.updateBookmark(f, "x", "https://x.com/", ROOT))
        assertFalse(repo.renameFolder(b, "Renamed"))
        assertFalse(repo.renameFolder("gone", "Renamed"))
        assertEquals(before, dao.nodeRows.value.toSet())
    }

    @Test fun anEditThatKeepsTheFolderKeepsThePosition() = runBlocking<Unit> {
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        val b = repo.addBookmark(ROOT, "B", "https://b.com/")!!
        assertTrue(repo.updateBookmark(a, "A2", "https://a2.com/", ROOT))
        assertEquals(listOf(a, b), order(ROOT))
    }

    @Test fun clearTodayInAZoneAheadOfUtcKeepsTheVisitJustBeforeLocalMidnight() = runBlocking<Unit> {
        val zone = ZoneId.of("Pacific/Auckland")
        clock = Instant.parse("2026-11-12T20:30:00Z").toEpochMilli()
        val midnight = Instant.parse("2026-11-12T11:00:00Z").toEpochMilli()
        repo.recordVisit("https://yesterday.com/", midnight - 1)
        repo.recordVisit("https://today.com/", midnight)
        repo.clearHistory(ClearRange.Today, zone)
        assertEquals(listOf("https://yesterday.com/"), dao.visits.map { it.url })
    }

    @Test fun clearTodayInAZoneBehindUtcCountsTheLocalDay() = runBlocking<Unit> {
        val zone = ZoneId.of("America/Los_Angeles")
        clock = Instant.parse("2026-11-13T03:00:00Z").toEpochMilli()
        val midnight = Instant.parse("2026-11-12T08:00:00Z").toEpochMilli()
        repo.recordVisit("https://old.com/", midnight - 1)
        repo.recordVisit("https://new.com/", midnight)
        repo.clearHistory(ClearRange.Today, zone)
        assertEquals(listOf("https://old.com/"), dao.visits.map { it.url })
    }

    @Test fun clearingAnEmptyHistoryTwiceIsHarmless() = runBlocking<Unit> {
        repeat(2) { ClearRange.entries.forEach { repo.clearHistory(it, ZoneId.of("UTC")) } }
        assertTrue(dao.visits.isEmpty())
    }

    @Test fun aVisitAtLocalMidnightBelongsToTheNewDay() {
        val zone = ZoneId.of("Europe/Moscow")
        val midnight = Instant.parse("2026-11-12T21:00:00Z").toEpochMilli()
        val days = groupByDay(listOf(HistoryEntity(1, "https://a/", "", midnight - 1), HistoryEntity(2, "https://b/", "", midnight)), zone)
        assertEquals(listOf(13, 12), days.map { it.date.dayOfMonth })
    }

    @Test fun twoVisitsAtTheSameMillisecondStayNewestIdFirst() {
        val rows = groupByDay(listOf(HistoryEntity(1, "https://a/", "", 5), HistoryEntity(2, "https://b/", "", 5)), ZoneId.of("UTC")).single().rows
        assertEquals(listOf("https://b/", "https://a/"), rows.map { it.url })
    }

    @Test fun aPageWithATitleEarlierThanAnUntitledRevisitKeepsItsTitle() {
        val rows = groupByDay(listOf(HistoryEntity(1, "https://a/", "Title", 5), HistoryEntity(2, "https://a/", "", 9)), ZoneId.of("UTC")).single().rows
        assertEquals("Title", rows.single().title)
        assertEquals(9, rows.single().visitedAt)
    }

    @Test fun searchFindsAnUppercaseUrlAndIgnoresBlankPaddingAroundTheQuery() {
        val all = listOf(HistoryEntity(1, "HTTPS://EXAMPLE.COM/", "", 1))
        assertEquals(all, searchHistory(all, "  example  "))
        assertEquals(all, searchHistory(all, "   "))
    }

    @Test fun searchFindsAnIstanbulTitleWithAPlainLowercaseI() {
        val all = listOf(HistoryEntity(1, "https://a.com/", "İstanbul", 1))
        assertEquals(1, searchHistory(all, "istanbul").size)
    }

    @Test fun theSamePageWithAnInternationalHostHasOneKeyWithOrWithoutTheSlash() {
        assertEquals(bookmarkUrlKey("https://пример.рф"), bookmarkUrlKey("https://пример.рф/"))
        assertEquals(bookmarkUrlKey("https://пример.рф/"), bookmarkUrlKey("https://ПРИМЕР.РФ/"))
    }

    @Test fun aHostWithAnUnderscoreIsStillNormalised() {
        assertEquals(bookmarkUrlKey("http://my_host.example.com"), bookmarkUrlKey("HTTP://My_Host.example.com/"))
    }

    @Test fun anIpv6HostIsAKeyOfItsOwn() {
        assertEquals(bookmarkUrlKey("http://[::1]:80/"), bookmarkUrlKey("HTTP://[::1]"))
        assertNotEquals(bookmarkUrlKey("http://[::1]/"), bookmarkUrlKey("http://[::2]/"))
    }

    @Test fun differentPortsAndSchemesAreDifferentPages() {
        assertNotEquals(bookmarkUrlKey("https://a.com/"), bookmarkUrlKey("http://a.com/"))
        assertNotEquals(bookmarkUrlKey("https://a.com:8443/"), bookmarkUrlKey("https://a.com/"))
    }

    @Test fun anEmptyOrGarbageUrlDoesNotThrowAndFindsNothing() {
        val nodes = listOf(BookmarkNodeEntity("b", ROOT, BookmarkNodeEntity.BOOKMARK, "t", "https://a.com/", 0, 1))
        assertNull(findBookmarkByUrl(nodes, ""))
        assertNull(findBookmarkByUrl(nodes, "http://[bad"))
        assertNull(findBookmarkByUrl(nodes, "\u0000"))
    }

    @Test fun aBookmarkWithAnEmptyUrlIsNotFoundForAnEmptyQuery() {
        val nodes = listOf(BookmarkNodeEntity("b", ROOT, BookmarkNodeEntity.BOOKMARK, "t", "", 0, 1), BookmarkNodeEntity(FAVORITES, ROOT, BookmarkNodeEntity.FOLDER, "", null, 0, 0))
        assertNull(findBookmarkByUrl(nodes, "https://a.com/"))
    }

    @Test fun recordableIgnoresSchemeCaseButNotLeadingSpace() {
        assertTrue(isRecordable("HTTPS://a.com/"))
        assertFalse(isRecordable(" https://a.com/"))
        assertFalse(isRecordable("view-source:https://a.com/"))
        assertFalse(isRecordable("about:blank"))
        assertFalse(isRecordable(""))
    }

    @Test fun bookmarkCountIgnoresFoldersWhenTheyAreFewerThanTheBookmarks() {
        fun n(id: String, parent: String, folder: Boolean) =
            BookmarkNodeEntity(id, parent, if (folder) BookmarkNodeEntity.FOLDER else BookmarkNodeEntity.BOOKMARK, id, if (folder) null else "https://$id/", 0, 0)
        val nodes = listOf(n("f", ROOT, true), n("sub", "f", true), n("a", "f", false), n("b", "f", false), n("c", "sub", false))
        assertEquals(3, bookmarkCount(nodes, "f"))
        assertEquals(1, bookmarkCount(nodes, "sub"))
    }

    @Test fun twoMovesRacingCannotMakeTwoFoldersEachOthersParent() = runBlocking<Unit> {
        val a = repo.createFolder(ROOT, "A")!!
        val b = repo.createFolder(ROOT, "B")!!
        val slow = object : LibraryDao by dao {
            override suspend fun nodes(): List<BookmarkNodeEntity> = dao.nodes().also { delay(20) }
        }
        val racing = LibraryRepository(slow, Dispatchers.Unconfined, now = { clock }, newId = { "x${++counter}" })
        val results = mutableListOf<Boolean>()
        val j1 = launch { results += racing.moveNode(a, b) }
        val j2 = launch { results += racing.moveNode(b, a) }
        j1.join(); j2.join()
        assertEquals(1, results.count { it })
        val nodes = dao.nodeRows.value
        val parents = listOf(a, b).associateWith { id -> nodes.first { it.id == id }.parentId }
        assertEquals(1, parents.values.count { it == ROOT })
        assertEquals(1, parents.values.count { it == a || it == b })
    }
}
