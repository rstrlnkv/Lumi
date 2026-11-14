package ru.digitalpunk.lumi.data.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private class Marker(var value: Boolean = false, val failSet: Boolean = false, val keep: Boolean = true) : SeedMarker {
    override suspend fun isSet() = value
    override suspend fun set() { if (failSet) throw IOException("disk full"); if (keep) value = true }
}

/** A DAO whose seed transaction fails, or reads the count and then waits before it inserts (an interleaving start). */
private class OddDao(
    private val d: FakeLibraryDao,
    private val failSeed: Boolean = false,
    private val failInsert: Boolean = false,
    private val pauseMs: Long = 0,
) : LibraryDao by d {
    override suspend fun insertIfAbsent(node: BookmarkNodeEntity): Long {
        if (failInsert && node.id == FAVORITES) throw IOException("locked")
        return d.insertIfAbsent(node)
    }

    override suspend fun seedFavorites(marked: Boolean, rows: List<BookmarkNodeEntity>): SeedAction {
        if (failSeed) throw IOException("locked")
        val action = seedAction(marked, d.childCount(FAVORITES))
        if (pauseMs > 0) delay(pauseMs)
        if (action == SeedAction.Seed) rows.forEach { d.insertIfAbsent(it) }
        return action
    }
}

class LibraryRepositorySeedEdgeTest {
    private val dao = FakeLibraryDao()
    private var counter = 0
    private fun repo(marker: SeedMarker?, on: LibraryDao = dao) =
        LibraryRepository(on, Dispatchers.Unconfined, now = { 7L }, newId = { "id${++counter}" }, seedMarker = marker)

    private fun favorites() = dao.nodeRows.value.filter { it.parentId == FAVORITES }.sortedBy { it.position }
    private val tenTitles = listOf("Google", "YouTube", "Wikipedia", "Gmail", "Amazon", "Reddit", "X", "Instagram", "GitHub", "Netflix")

    @Test fun theTenUrlsArePinnedLiterally() {
        assertEquals(
            listOf(
                "https://www.google.com/", "https://m.youtube.com/", "https://www.wikipedia.org/", "https://mail.google.com/",
                "https://www.amazon.com/", "https://www.reddit.com/", "https://x.com/", "https://www.instagram.com/",
                "https://github.com/", "https://www.netflix.com/",
            ),
            seedNodes(1L).map { it.url },
        )
        assertEquals(
            listOf("seed-google", "seed-youtube", "seed-wikipedia", "seed-gmail", "seed-amazon", "seed-reddit", "seed-x", "seed-instagram", "seed-github", "seed-netflix"),
            seedNodes(1L).map { it.id },
        )
    }

    @Test fun aUserWhoDeletedTheirOnlyFavoriteBeforeTheMarkerExistedIsNotSeededOnTheNextStart() = runBlocking<Unit> {
        val marker = Marker()
        repo(null).open()
        val mine = repo(null).addBookmark(FAVORITES, "Mine", "https://example.org/")!!
        repo(marker).open() // upgrade: marked, not seeded
        repo(marker).delete(mine)
        repo(marker).open()
        assertEquals(emptyList(), favorites())
    }

    @Test fun anEmptyFavoritesWithABookmarkInAnotherFolderIsSeededOnce() = runBlocking<Unit> {
        val d = FakeLibraryDao()
        d.insertIfAbsent(BookmarkNodeEntity(FAVORITES, LibraryIds.ROOT, BookmarkNodeEntity.FOLDER, "", null, 0, 0))
        d.insertIfAbsent(BookmarkNodeEntity("f", LibraryIds.ROOT, BookmarkNodeEntity.FOLDER, "F", null, 1, 0))
        d.insertIfAbsent(BookmarkNodeEntity("b", "f", BookmarkNodeEntity.BOOKMARK, "Other", "https://other.example/", 0, 0))
        LibraryRepository(d, Dispatchers.Unconfined, now = { 7L }, seedMarker = Marker()).open()
        assertEquals(tenTitles, d.nodeRows.value.filter { it.parentId == FAVORITES }.sortedBy { it.position }.map { it.title })
        assertEquals(1, d.nodeRows.value.count { it.id == "b" })
    }

    @Test fun aMarkerWriteFailureAfterTheSeedKeepsTenAndTheNextStartDoesNotAddMore() = runBlocking<Unit> {
        val marker = Marker(failSet = true)
        repo(marker).open()
        assertEquals(tenTitles, favorites().map { it.title })
        repo(marker).open()
        assertEquals(tenTitles, favorites().map { it.title })
    }

    @Test fun aLostMarkerAfterADeleteDoesNotBringTheDeletedSiteBack() = runBlocking<Unit> {
        val marker = Marker(keep = false)
        val r = repo(marker)
        r.open()
        r.delete("seed-reddit")
        repo(marker).open()
        assertEquals(tenTitles - "Reddit", favorites().map { it.title })
    }

    @Test fun aSeedTransactionFailureStillReleasesNodesAndLeavesTheMarkerUnset() = runBlocking<Unit> {
        val marker = Marker()
        val r = repo(marker, OddDao(dao, failSeed = true))
        r.open()
        val rows = withTimeout(5_000) { r.nodes.first() }
        assertEquals(listOf(FAVORITES), rows.map { it.id })
        assertFalse(marker.value)
        repo(marker).open()
        assertEquals(tenTitles, favorites().map { it.title })
    }

    @Test fun aFailingFavoritesCreateStillReleasesNodesAndStillPrunesHistory() = runBlocking<Unit> {
        val old = HistoryEntity(0, "https://a.example/", "A", 1L)
        dao.insertVisit(old)
        val r = LibraryRepository(OddDao(dao, failInsert = true), Dispatchers.Unconfined, now = { 100L * 24 * 3_600_000 }, seedMarker = Marker())
        r.open()
        withTimeout(5_000) { r.nodes.first() }
        assertEquals(emptyList(), dao.visits)
    }

    @Test fun twoRepositoriesStartingTogetherOnASlowDatabaseStillGiveTen() = runBlocking<Unit> {
        val marker = Marker()
        val slow = OddDao(dao, pauseMs = 40)
        val rs = listOf(repo(marker, slow), repo(marker, slow), repo(marker, slow))
        rs.map { async(Dispatchers.Default) { it.open() } }.awaitAll()
        assertEquals(tenTitles, favorites().map { it.title })
        assertEquals(10, dao.nodeRows.value.count { it.kind == BookmarkNodeEntity.BOOKMARK })
        assertTrue(marker.value)
    }

    @Test fun nodesCollectedBeforeOpenNeverShowAnEmptyFavoritesFirst() = runBlocking<Unit> {
        val r = repo(Marker())
        val seen = async(Dispatchers.Default) { r.nodes.first() }
        delay(50)
        r.open()
        assertEquals(10, withTimeout(5_000) { seen.await() }.count { it.parentId == FAVORITES })
    }
}
