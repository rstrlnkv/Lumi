package ru.digitalpunk.lumi.data.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** An in-memory [LibraryDao] for the repository tests. */
internal class FakeLibraryDao : LibraryDao {
    val nodeRows = MutableStateFlow(emptyList<BookmarkNodeEntity>())
    var visits = emptyList<HistoryEntity>()
    private var nextVisitId = 1L

    override fun observeNodes(): Flow<List<BookmarkNodeEntity>> = nodeRows
    override suspend fun nodes() = nodeRows.value
    override suspend fun insertIfAbsent(node: BookmarkNodeEntity): Long {
        if (nodeRows.value.any { it.id == node.id }) return -1
        nodeRows.value += node
        return 1
    }
    override suspend fun insertAll(nodes: List<BookmarkNodeEntity>) {
        val ids = nodes.map { it.id }.toSet()
        nodeRows.value = nodeRows.value.filterNot { it.id in ids } + nodes
    }
    override suspend fun update(node: BookmarkNodeEntity) {
        nodeRows.value = nodeRows.value.map { if (it.id == node.id) node else it }
    }
    override suspend fun deleteNodes(ids: List<String>) {
        nodeRows.value = nodeRows.value.filterNot { it.id in ids }
    }
    override suspend fun nextPosition(parent: String) = (nodeRows.value.filter { it.parentId == parent }.maxOfOrNull { it.position } ?: -1) + 1

    override suspend fun insertVisit(entry: HistoryEntity): Long {
        val id = nextVisitId++
        visits = visits + entry.copy(id = id)
        return id
    }
    override suspend fun titleLatest(url: String, title: String) {
        val latest = visits.filter { it.url == url }.maxWithOrNull(compareBy({ it.visitedAt }, { it.id })) ?: return
        visits = visits.map { if (it.id == latest.id) it.copy(title = title) else it }
    }
    override fun observeHistory(limit: Int): Flow<List<HistoryEntity>> = MutableStateFlow(visits).map { it.take(limit) }
    override suspend fun insertVisits(entries: List<HistoryEntity>) {
        val ids = entries.map { it.id }.toSet()
        visits = visits.filterNot { it.id in ids } + entries
    }
    override suspend fun deleteVisits(ids: List<Long>) { visits = visits.filterNot { it.id in ids } }
    override suspend fun deleteVisitsSince(from: Long) { visits = visits.filterNot { it.visitedAt >= from } }
    override suspend fun deleteAllVisits() { visits = emptyList() }
    override suspend fun pruneVisits(cutoff: Long) { visits = visits.filterNot { it.visitedAt < cutoff } }
}

class LibraryRepositoryTest {
    private val dao = FakeLibraryDao()
    private var clock = 1_000_000_000_000L
    private var counter = 0
    private val repo = LibraryRepository(dao, Dispatchers.Unconfined, now = { clock }, newId = { "id${++counter}" })

    private fun node(id: String) = dao.nodeRows.value.single { it.id == id }

    @Test fun openSeedsFavoritesOnceWithAnEmptyTitle() = runBlocking<Unit> {
        repo.open()
        repo.renameFolder(FAVORITES, "Mine")
        repo.open()
        assertEquals(listOf(FAVORITES), dao.nodeRows.value.map { it.id })
        assertEquals("Mine", node(FAVORITES).title)
        assertEquals(ROOT, node(FAVORITES).parentId)
    }

    @Test fun newNodesAreAppendedAtTheEndOfTheirFolder() = runBlocking<Unit> {
        repo.open()
        val f = repo.createFolder(ROOT, "Work")!!
        val b = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        val inside = repo.addBookmark(f, "B", "https://b.com/")!!
        assertEquals(listOf(0, 1, 2), listOf(node(FAVORITES), node(f), node(b)).map { it.position })
        assertEquals(0, node(inside).position)
    }

    @Test fun aBlankTitleIsSavedAsTheHost() = runBlocking<Unit> {
        val id = repo.addBookmark(ROOT, "  ", "https://news.example.com/a")!!
        assertEquals("news.example.com", node(id).title)
    }

    @Test fun anEmptyFolderNameOrUrlIsRefused() = runBlocking<Unit> {
        repo.open()
        assertNull(repo.createFolder(ROOT, ""))
        assertFalse(repo.renameFolder(FAVORITES, ""))
        assertNull(repo.addBookmark(ROOT, "x", ""))
        val id = repo.addBookmark(ROOT, "x", "https://x.com/")!!
        assertFalse(repo.updateBookmark(id, "x", "", ROOT))
        assertEquals("https://x.com/", node(id).url)
    }

    @Test fun deleteReturnsTheSubtreeAndRestorePutsItBackAtTheSamePositions() = runBlocking<Unit> {
        repo.open()
        val f = repo.createFolder(ROOT, "Work")!!
        val sub = repo.createFolder(f, "Sub")!!
        val b = repo.addBookmark(sub, "B", "https://b.com/")!!
        val after = repo.addBookmark(ROOT, "C", "https://c.com/")!!
        val before = dao.nodeRows.value.toSet()
        val removed = repo.delete(f)!!
        assertEquals(setOf(f, sub, b), removed.map { it.id }.toSet())
        assertEquals(setOf(FAVORITES, after), dao.nodeRows.value.map { it.id }.toSet())
        repo.restore(removed)
        assertEquals(before, dao.nodeRows.value.toSet())
    }

    @Test fun favoritesCannotBeDeletedOrMoved() = runBlocking<Unit> {
        repo.open()
        val f = repo.createFolder(ROOT, "Work")!!
        assertNull(repo.delete(FAVORITES))
        assertFalse(repo.moveNode(FAVORITES, f))
        assertEquals(ROOT, node(FAVORITES).parentId)
    }

    @Test fun restoreUnderAParentThatIsGoneGoesToTheRoot() = runBlocking<Unit> {
        val f = repo.createFolder(ROOT, "Work")!!
        val b = repo.addBookmark(f, "B", "https://b.com/")!!
        val removed = repo.delete(b)!!
        repo.delete(f)
        repo.restore(removed)
        assertEquals(ROOT, node(b).parentId)
    }

    @Test fun moveAppendsAtTheEndAndRefusesAMoveIntoItsOwnSubtree() = runBlocking<Unit> {
        val a = repo.createFolder(ROOT, "A")!!
        val child = repo.createFolder(a, "Child")!!
        val b = repo.createFolder(ROOT, "B")!!
        repo.addBookmark(b, "x", "https://x.com/")
        assertFalse(repo.moveNode(a, child))
        assertTrue(repo.moveNode(a, b))
        assertEquals(b, node(a).parentId)
        assertEquals(1, node(a).position)
    }

    @Test fun updateBookmarkChangesTitleUrlAndFolder() = runBlocking<Unit> {
        val f = repo.createFolder(ROOT, "F")!!
        val id = repo.addBookmark(ROOT, "Old", "https://old.com/")!!
        assertTrue(repo.updateBookmark(id, "New", "https://new.com/", f))
        val n = node(id)
        assertEquals(listOf("New", "https://new.com/", f), listOf(n.title, n.url, n.parentId))
    }

    @Test fun titleVisitNamesOnlyTheNewestVisitOfThatUrl() = runBlocking<Unit> {
        repo.recordVisit("https://a.com/", 10)
        repo.recordVisit("https://a.com/", 20)
        repo.recordVisit("https://b.com/", 30)
        repo.titleVisit("https://a.com/", "A")
        assertEquals(listOf("", "A", ""), dao.visits.sortedBy { it.visitedAt }.map { it.title })
    }

    @Test fun openPrunesOnlyVisitsOlderThanNinetyDays() = runBlocking<Unit> {
        val cutoff = clock - 90L * 24 * 3_600_000
        repo.recordVisit("https://old.com/", cutoff - 1)
        repo.recordVisit("https://edge.com/", cutoff)
        repo.open()
        assertEquals(listOf("https://edge.com/"), dao.visits.map { it.url })
    }

    @Test fun deletedVisitsComeBackOnRestore() = runBlocking<Unit> {
        repo.recordVisit("https://a.com/", 10)
        repo.recordVisit("https://a.com/", 20)
        val rows = dao.visits
        repo.deleteVisits(rows.map { it.id })
        assertTrue(dao.visits.isEmpty())
        repo.restoreVisits(rows)
        assertEquals(rows.toSet(), dao.visits.toSet())
    }

    @Test fun clearHistoryRemovesTheRangeOrEverything() = runBlocking<Unit> {
        repo.recordVisit("https://old.com/", clock - 3_600_001)
        repo.recordVisit("https://new.com/", clock - 3_600_000)
        repo.clearHistory(ClearRange.LastHour, ZoneId.of("UTC"))
        assertEquals(listOf("https://old.com/"), dao.visits.map { it.url })
        repo.clearHistory(ClearRange.AllTime, ZoneId.of("UTC"))
        assertNotNull(dao.visits)
        assertTrue(dao.visits.isEmpty())
    }
    @Test fun titlesLongerThan256CharactersAreCutWhenStored() = runBlocking<Unit> {
        val long = "t".repeat(300)
        val b = repo.addBookmark(ROOT, long, "https://a.com/")!!
        assertEquals(256, node(b).title.length)
        val c = repo.addBookmark(ROOT, "C", "https://c.com/")!!
        assertTrue(repo.updateBookmark(c, long, "https://c.com/", ROOT))
        assertEquals(256, node(c).title.length)
        val f = repo.createFolder(ROOT, long)!!
        assertEquals(256, node(f).title.length)
        assertTrue(repo.renameFolder(f, long))
        assertEquals(256, node(f).title.length)
        repo.recordVisit("https://a.com/", 1)
        repo.titleVisit("https://a.com/", long)
        assertEquals(256, dao.visits.single().title.length)
    }

    @Test fun aBookmarkUrlLongerThan2048CharactersIsRefused() = runBlocking<Unit> {
        val long = "https://a.com/" + "p".repeat(2048)
        assertNull(repo.addBookmark(ROOT, "A", long))
        val b = repo.addBookmark(ROOT, "B", "https://b.com/")!!
        assertFalse(repo.updateBookmark(b, "B", long, ROOT))
        assertEquals("https://b.com/", node(b).url)
    }
}
