package ru.digitalpunk.lumi.data.library

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import ru.digitalpunk.lumi.data.tabs.TabDatabase
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The real SQL behind [LibraryRepository], on a phone or emulator: `./gradlew :data:connectedDebugAndroidTest`. */
class LibraryDaoDeviceTest {
    private lateinit var db: TabDatabase
    private lateinit var dao: LibraryDao
    private var clock = Instant.parse("2026-11-13T12:00:00Z").toEpochMilli()
    private var counter = 0
    private lateinit var repo: LibraryRepository

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, TabDatabase::class.java).build()
        dao = db.libraryDao()
        repo = LibraryRepository(dao, Dispatchers.IO, now = { clock }, newId = { "n${counter++}" })
    }

    @After fun close() = db.close()

    private fun urls() = runBlocking { repo.history.first().map { it.url } }

    @Test fun openTwiceKeepsOneFavoritesAndTheRenameSurvives() = runBlocking<Unit> {
        repo.open()
        repo.renameFolder(FAVORITES, "Mine")
        repo.open()
        val all = repo.nodes.first()
        assertEquals(listOf(FAVORITES), all.map { it.id })
        assertEquals("Mine", all.single().title)
    }

    @Test fun nodesComeBackInParentThenPositionOrder() = runBlocking<Unit> {
        repo.open()
        val f = repo.createFolder(ROOT, "F")!!
        val a = repo.addBookmark(f, "A", "https://a.com/")!!
        val b = repo.addBookmark(f, "B", "https://b.com/")!!
        repo.moveNode(a, ROOT)
        assertEquals(listOf(b), children(repo.nodes.first(), f).map { it.id })
        assertEquals(listOf(FAVORITES, f, a), children(repo.nodes.first(), ROOT).map { it.id })
    }

    @Test fun titleLandsOnTheNewestVisitOfTheUrlAndOnNothingForAnUnknownUrl() = runBlocking<Unit> {
        repo.recordVisit("https://a.com/", 20)
        repo.recordVisit("https://a.com/", 10)
        repo.recordVisit("https://a.com/", 20)
        repo.titleVisit("https://a.com/", "A")
        repo.titleVisit("https://none.com/", "X")
        val rows = repo.history.first()
        assertEquals(listOf("A", "", ""), rows.map { it.title })
        assertEquals(rows.maxOf { it.id }, rows.first { it.title == "A" }.id)
    }

    @Test fun historyIsNewestFirstAndCappedAt5000() = runBlocking<Unit> {
        repeat(5100) { dao.insertVisit(HistoryEntity(url = "https://a.com/$it", title = "", visitedAt = it.toLong())) }
        val rows = repo.history.first()
        assertEquals(5000, rows.size)
        assertEquals("https://a.com/5099", rows.first().url)
        assertEquals("https://a.com/100", rows.last().url)
    }

    @Test fun clearLastHourIncludesTheExactBoundaryAndKeepsOlder() = runBlocking<Unit> {
        repo.recordVisit("https://older.com/", clock - 3_600_001)
        repo.recordVisit("https://edge.com/", clock - 3_600_000)
        repo.recordVisit("https://now.com/", clock)
        repo.clearHistory(ClearRange.LastHour, ZoneId.of("UTC"))
        assertEquals(listOf("https://older.com/"), urls())
    }

    @Test fun clearTodayUsesTheZoneOfTheCaller() = runBlocking<Unit> {
        val zone = ZoneId.of("Asia/Kolkata")
        clock = Instant.parse("2026-11-13T10:00:00Z").toEpochMilli()
        val midnight = Instant.parse("2026-11-12T18:30:00Z").toEpochMilli()
        repo.recordVisit("https://before.com/", midnight - 1)
        repo.recordVisit("https://after.com/", midnight)
        repo.clearHistory(ClearRange.Today, zone)
        assertEquals(listOf("https://before.com/"), urls())
    }

    @Test fun clearAllEmptiesHistoryButNotBookmarks() = runBlocking<Unit> {
        repo.open()
        repo.addBookmark(ROOT, "A", "https://a.com/")
        repo.recordVisit("https://a.com/", clock)
        repo.clearHistory(ClearRange.AllTime, ZoneId.of("UTC"))
        assertTrue(repo.history.first().isEmpty())
        assertEquals(2, repo.nodes.first().size)
    }

    @Test fun deletingAndRestoringAFolderOfThirtyFiveThousandBookmarksWorks() = runBlocking<Unit> {
        repo.open()
        val f = repo.createFolder(ROOT, "Big")!!
        dao.insertAll(List(35_000) { BookmarkNodeEntity("big$it", f, BookmarkNodeEntity.BOOKMARK, "t$it", "https://a.com/$it", it, it.toLong()) })
        val removed = repo.delete(f)
        assertNotNull(removed)
        assertEquals(35_001, removed.size)
        assertEquals(listOf(FAVORITES), repo.nodes.first().map { it.id })
        repo.restore(removed)
        assertEquals(35_002, repo.nodes.first().size)
    }

    @Test fun deletingThirtyFiveThousandVisitsByIdWorks() = runBlocking<Unit> {
        dao.insertVisits(List(35_000) { HistoryEntity(it + 1L, "https://a.com/$it", "", it.toLong()) })
        val all = dao.observeHistory(40_000).first()
        repo.deleteVisits(all.map { it.id })
        assertTrue(dao.observeHistory(40_000).first().isEmpty())
    }

    @Test fun twoHundredParallelAddsGetTwoHundredDifferentPositions() = runBlocking<Unit> {
        repo.open()
        List(200) { i -> async(Dispatchers.Default) { repo.addBookmark(ROOT, "t$i", "https://a.com/$i") } }.awaitAll()
        val positions = repo.nodes.first().map { it.position }
        assertEquals(201, positions.size)
        assertEquals(positions.size, positions.toSet().size)
    }

    @Test fun aDaoThatThrowsIsReportedAsFailureAndNeverEscapes() = runBlocking<Unit> {
        val broken = object : LibraryDao by dao {
            override suspend fun nodes(): List<BookmarkNodeEntity> = throw IllegalStateException("disk full")
            override suspend fun insertVisit(entry: HistoryEntity): Long = throw IllegalStateException("disk full")
            override suspend fun deleteAllVisits() = throw IllegalStateException("disk full")
        }
        val bad = LibraryRepository(broken, Dispatchers.IO)
        assertNull(bad.delete("x"))
        assertFalse(bad.moveNode("x", ROOT))
        assertFalse(bad.renameFolder("x", "y"))
        assertFalse(bad.updateBookmark("x", "t", "https://a.com/", ROOT))
        bad.recordVisit("https://a.com/", 1)
        bad.clearHistory(ClearRange.AllTime, ZoneId.of("UTC"))
        bad.restore(emptyList())
        assertEquals(1, bad.addBookmark(ROOT, "t", "https://a.com/")?.let { 1 })
    }
}
