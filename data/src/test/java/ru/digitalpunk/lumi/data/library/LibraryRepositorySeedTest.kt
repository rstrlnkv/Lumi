package ru.digitalpunk.lumi.data.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A marker in memory; [keep] false loses every write (a crash before the marker is saved), [failRead] throws on read. */
private class FakeMarker(var value: Boolean = false, val keep: Boolean = true, var failRead: Boolean = false) : SeedMarker {
    override suspend fun isSet(): Boolean = if (failRead) throw IOException("disk") else value
    override suspend fun set() { if (keep) value = true }
}

class LibraryRepositorySeedTest {
    private val dao = FakeLibraryDao()
    private var counter = 0

    private fun repo(marker: SeedMarker?) =
        LibraryRepository(dao, Dispatchers.Unconfined, now = { 7L }, newId = { "id${++counter}" }, seedMarker = marker)

    private fun favorites() = dao.nodeRows.value.filter { it.parentId == FAVORITES }.sortedBy { it.position }
    private val tenTitles = listOf("Google", "YouTube", "Wikipedia", "Gmail", "Amazon", "Reddit", "X", "Instagram", "GitHub", "Netflix")

    @Test fun openSeedsTenBookmarksInOrderAndSetsTheMarker() = runBlocking<Unit> {
        val marker = FakeMarker()
        repo(marker).open()
        assertEquals(tenTitles, favorites().map { it.title })
        assertTrue(marker.value)
    }

    @Test fun openTwiceKeepsTenBookmarksInOrder() = runBlocking<Unit> {
        val repo = repo(FakeMarker())
        repo.open()
        repo.open()
        assertEquals(tenTitles, favorites().map { it.title })
    }

    @Test fun aLostMarkerWriteStillGivesTenBookmarksOnTheNextStart() = runBlocking<Unit> {
        val marker = FakeMarker(keep = false)
        repo(marker).open()
        repo(marker).open()
        assertEquals(tenTitles, favorites().map { it.title })
    }

    @Test fun aDeletedSeedBookmarkDoesNotComeBack() = runBlocking<Unit> {
        val marker = FakeMarker()
        val repo = repo(marker)
        repo.open()
        repo.delete("seed-reddit")
        repo(marker).open()
        assertEquals(tenTitles - "Reddit", favorites().map { it.title })
    }

    @Test fun anExistingFavoriteMarksTheLibraryWithoutSeeding() = runBlocking<Unit> {
        val marker = FakeMarker()
        val repo = repo(marker)
        repo(null).open()
        repo.addBookmark(FAVORITES, "Mine", "https://example.org/")
        repo.open()
        assertEquals(listOf("Mine"), favorites().map { it.title })
        assertTrue(marker.value)
    }

    @Test fun aMarkedLibraryWithAnEmptyFavoritesStaysEmpty() = runBlocking<Unit> {
        repo(FakeMarker(value = true)).open()
        assertEquals(emptyList(), favorites())
    }

    @Test fun concurrentOpensGiveTenBookmarks() = runBlocking<Unit> {
        val repo = repo(FakeMarker())
        (1..4).map { async(Dispatchers.Default) { repo.open() } }.awaitAll()
        assertEquals(tenTitles, favorites().map { it.title })
    }

    @Test fun aMarkerReadFailureSeedsNothingButCreatesFavoritesAndTheNextStartSeeds() = runBlocking<Unit> {
        val marker = FakeMarker(failRead = true)
        repo(marker).open()
        assertEquals(listOf(FAVORITES), dao.nodeRows.value.map { it.id })
        assertFalse(marker.value)
        marker.failRead = false
        repo(marker).open()
        assertEquals(tenTitles, favorites().map { it.title })
    }

    @Test fun nodesWaitForOpenAndFirstEmitTheSeededFavorites() = runBlocking<Unit> {
        val repo = repo(FakeMarker())
        val first = async(Dispatchers.Unconfined) { repo.nodes.first() }
        assertFalse(first.isCompleted)
        repo.open()
        assertEquals(tenTitles, first.await().filter { it.parentId == FAVORITES }.sortedBy { it.position }.map { it.title })
    }

    @Test fun openWithoutAMarkerSeedsNothing() = runBlocking<Unit> {
        repo(null).open()
        assertEquals(emptyList(), favorites())
    }
}
