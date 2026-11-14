package ru.digitalpunk.lumi.data.library

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Inputs the happy-path reorder tests do not feed: empty and single lists, extreme indexes, folders, ties, failing writes. */
class ReorderEdgeTest {
    private val fake = FakeLibraryDao()
    private var counter = 0
    private val repo = LibraryRepository(fake, Dispatchers.Unconfined, now = { 1_000L }, newId = { "id${++counter}" })

    private fun order(parent: String) = children(fake.nodeRows.value, parent).map { it.id }

    @Test fun moveToIndexOnAnEmptyListStaysEmpty() {
        assertEquals(emptyList(), moveToIndex(emptyList(), "a", 0))
        assertEquals(emptyList(), moveToIndex(emptyList(), "a", Int.MAX_VALUE))
    }

    @Test fun moveToIndexOfTheOnlyItemKeepsItWhateverTheIndex() {
        for (to in listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE)) assertEquals(listOf("a"), moveToIndex(listOf("a"), "a", to))
    }

    @Test fun moveToIndexAtTheExactLastIndexAndOnePastItBothPutTheItemLast() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), moveToIndex(ids, "a", 2))
        assertEquals(listOf("b", "c", "a"), moveToIndex(ids, "a", 3))
        assertEquals(listOf("b", "a", "c"), moveToIndex(ids, "a", 1))
    }

    @Test fun moveToIndexWithIntExtremesClampsInsteadOfThrowing() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("c", "a", "b"), moveToIndex(ids, "c", Int.MIN_VALUE))
        assertEquals(listOf("b", "c", "a"), moveToIndex(ids, "a", Int.MAX_VALUE))
    }

    @Test fun moveToIndexMovingTheLastItemToZeroAndTheFirstItemOneStepWorks() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("c", "a", "b"), moveToIndex(ids, "c", 0))
        assertEquals(listOf("b", "a", "c"), moveToIndex(ids, "a", 1))
    }

    @Test fun reorderedOfASingleChildFolderIsEmptyForAnyIndex() {
        val rows = listOf(folder("f"), bookmark("only", "f", position = 0))
        for (to in listOf(Int.MIN_VALUE, 0, 1, Int.MAX_VALUE)) assertEquals(emptyList(), reordered(rows, "only", to))
    }

    @Test fun reorderedOfASingleChildWithAStalePositionRenumbersItToZero() {
        val rows = listOf(folder("f"), bookmark("only", "f", position = 7))
        assertEquals(listOf(0), reordered(rows, "only", 0).map { it.position })
    }

    @Test fun reorderedInsideANonFavoritesFolderLeavesFavoritesAndRootUntouched() {
        val rows = listOf(
            folder(FAVORITES), bookmark("fav1", FAVORITES, 0), bookmark("fav2", FAVORITES, 1),
            folder("work", position = 1), bookmark("w1", "work", 0), bookmark("w2", "work", 1), bookmark("w3", "work", 2),
        )
        val changed = reordered(rows, "w3", 0)
        assertEquals(mapOf("w3" to 0, "w1" to 1, "w2" to 2), changed.associate { it.id to it.position })
        assertTrue(changed.all { it.parentId == "work" })
    }

    @Test fun reorderedOfAFolderNodeMovesTheFolderAndKeepsItsChildrenWhereTheyAre() {
        val rows = listOf(folder(FAVORITES, position = 0), folder("work", position = 1), folder("misc", position = 2), bookmark("w1", "work", 0))
        val changed = reordered(rows, "misc", 0)
        assertEquals(mapOf("misc" to 0, "work" to 2, FAVORITES to 1), changed.associate { it.id to it.position })
        assertTrue(changed.none { it.id == "w1" })
        assertTrue(changed.all { it.parentId == ROOT && it.isFolder })
    }

    @Test fun reorderedOfAMovedFolderKeepsItsOtherFieldsUnchanged() {
        val rows = listOf(folder("a", position = 0, title = "A", createdAt = 5), folder("b", position = 1, title = "B", createdAt = 6))
        assertEquals(listOf(folder("b", position = 0, title = "B", createdAt = 6), folder("a", position = 1, title = "A", createdAt = 5)).toSet(), reordered(rows, "b", 0).toSet())
    }

    @Test fun reorderedWithAllPositionsTiedGivesEveryoneAUniqueNumber() {
        val rows = listOf(bookmark("c", createdAt = 3), bookmark("a", createdAt = 1), bookmark("b", createdAt = 2))
        val applied = rows.map { r -> reordered(rows, "c", 0).firstOrNull { it.id == r.id } ?: r }
        assertEquals(listOf("c", "a", "b"), children(applied, ROOT).map { it.id })
        assertEquals(setOf(0, 1, 2), applied.map { it.position }.toSet())
    }

    @Test fun reorderedWithATiedEqualPositionAsTheTargetStillPlacesTheMovedNodeBefore() {
        val rows = listOf(bookmark("a", position = 0), bookmark("b", position = 0), bookmark("c", position = 0))
        val applied = rows.map { r -> reordered(rows, "c", 1).firstOrNull { it.id == r.id } ?: r }
        assertEquals(listOf("a", "c", "b"), children(applied, ROOT).map { it.id })
    }

    @Test fun reorderedOfANodeWhoseParentIsABookmarkIsEmpty() {
        // childMap shows such an orphan under ROOT, so the user can drag it; reordered refuses it.
        val rows = listOf(bookmark("b1", position = 0), bookmark("orphan", "b1", position = 1))
        assertEquals(listOf("b1", "orphan"), children(rows, ROOT).map { it.id })
        assertEquals(emptyList(), reordered(rows, "orphan", 0))
    }

    @Test fun reorderOfAnOrphanWritesNothingAndReturnsFalse() = runBlocking<Unit> {
        repo.open()
        val b = repo.addBookmark(ROOT, "B", "https://b.com/")!!
        fake.insertAll(listOf(bookmark("orphan", b, position = 1)))
        val rows = fake.nodeRows.value
        assertFalse(repo.reorder("orphan", 0))
        assertEquals(rows, fake.nodeRows.value)
    }

    @Test fun reorderedOfADuplicateIdRowDoesNotThrow() {
        val rows = listOf(bookmark("a", position = 0), bookmark("a", position = 1), bookmark("b", position = 2))
        reordered(rows, "b", 0)
    }

    @Test fun reorderInTheFolderAsTheFavoritesFolderItselfMovesFavoritesInTheRoot() = runBlocking<Unit> {
        repo.open()
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        assertTrue(repo.reorder(FAVORITES, 1))
        assertEquals(listOf(a, FAVORITES), order(ROOT))
    }

    @Test fun reorderOfAFolderMovesItsSubtreeWithIt() = runBlocking<Unit> {
        repo.open()
        val f = repo.createFolder(ROOT, "F")!!
        val inside = repo.addBookmark(f, "I", "https://i.com/")!!
        val a = repo.addBookmark(ROOT, "A", "https://a.com/")!!
        assertTrue(repo.reorder(f, 5))
        assertEquals(listOf(FAVORITES, a, f), order(ROOT))
        assertEquals(listOf(inside), order(f))
    }

    @Test fun reorderInsideANonFavoritesFolderRenumbersOnlyThatFolder() = runBlocking<Unit> {
        repo.open()
        val f = repo.createFolder(ROOT, "F")!!
        val x = repo.addBookmark(f, "X", "https://x.com/")!!
        val y = repo.addBookmark(f, "Y", "https://y.com/")!!
        val z = repo.addBookmark(f, "Z", "https://z.com/")!!
        val rootBefore = children(fake.nodeRows.value, ROOT)
        assertTrue(repo.reorder(z, 0))
        assertEquals(listOf(z, x, y), order(f))
        assertEquals(rootBefore, children(fake.nodeRows.value, ROOT))
    }

    @Test fun reorderOfTheOnlyChildChangesNothingAndReturnsFalse() = runBlocking<Unit> {
        repo.open()
        val f = repo.createFolder(ROOT, "F")!!
        val only = repo.addBookmark(f, "X", "https://x.com/")!!
        val rows = fake.nodeRows.value
        assertFalse(repo.reorder(only, 9))
        assertEquals(rows, fake.nodeRows.value)
    }

    @Test fun reorderTwiceInARowWithTheSameArgumentsEqualsOnce() = runBlocking<Unit> {
        repo.open()
        repo.addBookmark(ROOT, "A", "https://a.com/")
        val b = repo.addBookmark(ROOT, "B", "https://b.com/")!!
        repo.reorder(b, 0)
        val once = fake.nodeRows.value
        assertFalse(repo.reorder(b, 0))
        assertEquals(once, fake.nodeRows.value)
    }

    @Test fun reorderFromTwoCoroutinesAtOnceLeavesUniquePositions() = runBlocking<Unit> {
        repo.open()
        val ids = (1..6).map { repo.addBookmark(ROOT, "N$it", "https://n$it.com/")!! }
        val jobs = listOf(ids[5] to 0, ids[0] to 5, ids[3] to 1, ids[1] to 4).map { (id, to) -> launch(Dispatchers.Default) { repo.reorder(id, to) } }
        jobs.forEach { it.join() }
        val positions = children(fake.nodeRows.value, ROOT).map { it.position }
        assertEquals(positions.size, positions.toSet().size)
        assertEquals(7, positions.size)
    }

    @Test fun aFailingWriteMakesReorderReturnFalseAndLeavesTheRowsAsTheyWere() = runBlocking<Unit> {
        repo.open()
        repo.addBookmark(ROOT, "A", "https://a.com/")
        val b = repo.addBookmark(ROOT, "B", "https://b.com/")!!
        val failing = object : LibraryDao by fake {
            override suspend fun insertAll(nodes: List<BookmarkNodeEntity>) = throw IllegalStateException("disk full")
        }
        val rows = fake.nodeRows.value
        val broken = LibraryRepository(failing, Dispatchers.Unconfined, now = { 1_000L }, newId = { "x" })
        assertFalse(broken.reorder(b, 0))
        assertEquals(rows, fake.nodeRows.value)
    }
}
