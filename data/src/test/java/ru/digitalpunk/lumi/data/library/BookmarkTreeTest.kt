package ru.digitalpunk.lumi.data.library

import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

internal fun folder(id: String, parent: String = ROOT, position: Int = 0, title: String = id, createdAt: Long = 0) =
    BookmarkNodeEntity(id, parent, BookmarkNodeEntity.FOLDER, title, null, position, createdAt)

internal fun bookmark(id: String, parent: String = ROOT, position: Int = 0, url: String = "https://$id.com/", createdAt: Long = 0) =
    BookmarkNodeEntity(id, parent, BookmarkNodeEntity.BOOKMARK, id, url, position, createdAt)

class BookmarkTreeTest {
    // root: favorites(0), work(1), b1(2); work: docs(0), b2(1); docs: b3(0), deep(1); deep: (empty)
    private val nodes = listOf(
        folder(FAVORITES, position = 0, title = ""),
        folder("work", position = 1),
        bookmark("b1", position = 2),
        folder("docs", "work", 0),
        bookmark("b2", "work", 1),
        bookmark("b3", "docs", 0),
        folder("deep", "docs", 1),
    )

    @Test fun childrenAreInPositionOrderWithFoldersAndBookmarksMixed() {
        assertEquals(listOf(FAVORITES, "work", "b1"), children(nodes, ROOT).map { it.id })
        assertEquals(listOf("docs", "b2"), children(nodes, "work").map { it.id })
    }

    @Test fun childrenTiesAreBrokenByCreationThenId() {
        val tied = listOf(folder("z", createdAt = 1), folder("y", createdAt = 2), folder("x", createdAt = 1))
        assertEquals(listOf("x", "z", "y"), children(tied, ROOT).map { it.id })
    }

    @Test fun flattenListsRootThenFoldersDepthFirstWithoutBookmarks() {
        assertEquals(
            listOf(ROOT to 0, FAVORITES to 1, "work" to 1, "docs" to 2, "deep" to 3),
            flatten(nodes).map { it.id to it.depth },
        )
    }

    @Test fun aFolderCannotMoveIntoItselfItsChildOrItsGrandchild() {
        assertFalse(canMoveNode(nodes, "work", "work"))
        assertFalse(canMoveNode(nodes, "work", "docs"))
        assertFalse(canMoveNode(nodes, "work", "deep"))
    }

    @Test fun aFolderCanMoveIntoASiblingOrTheRoot() {
        assertTrue(canMoveNode(nodes, "work", FAVORITES))
        assertTrue(canMoveNode(nodes, "docs", ROOT))
    }

    @Test fun aBookmarkCanMoveToAnyFolderButNotIntoABookmarkOrAnUnknownFolder() {
        assertTrue(canMoveNode(nodes, "b3", ROOT))
        assertTrue(canMoveNode(nodes, "b3", "deep"))
        assertFalse(canMoveNode(nodes, "b3", "b1"))
        assertFalse(canMoveNode(nodes, "b3", "gone"))
        assertFalse(canMoveNode(nodes, "gone", ROOT))
    }

    @Test fun favoritesCannotBeMovedOrDeletedButIsAFolderLikeAnyOther() {
        assertFalse(canMoveNode(nodes, FAVORITES, "work"))
        assertFalse(canDelete(FAVORITES))
        assertTrue(canDelete("work"))
        assertTrue(canMoveNode(nodes, "b1", FAVORITES))
    }

    @Test fun aCycleInCorruptDataDoesNotHang() {
        val cyclic = listOf(folder("a", "b"), folder("b", "a"), bookmark("x", "a"))
        assertEquals(setOf("b"), descendantFolderIds(cyclic, "a"))
        assertEquals(1, bookmarkCount(cyclic, "a"))
        assertEquals(setOf("a", "b", "x"), subtree(cyclic, "a").map { it.id }.toSet())
        assertEquals(listOf(ROOT), flatten(cyclic).map { it.id })
        assertFalse(canMoveNode(cyclic, "a", "b"))
    }

    @Test fun aNodeWhoseParentIsGoneShowsUnderTheRoot() {
        val orphans = listOf(folder("f"), bookmark("lost", "deleted-folder"), bookmark("underBookmark", "lost"))
        assertEquals(listOf("f", "lost", "underBookmark"), children(orphans, ROOT).map { it.id })
    }

    @Test fun bookmarkCountCountsTheWholeSubtree() {
        assertEquals(2, bookmarkCount(nodes, "work"))
        assertEquals(1, bookmarkCount(nodes, "docs"))
        assertEquals(0, bookmarkCount(nodes, "deep"))
    }

    @Test fun subtreeHoldsTheNodeAndEverythingBelowIt() {
        assertEquals(setOf("work", "docs", "b2", "b3", "deep"), subtree(nodes, "work").map { it.id }.toSet())
        assertEquals(listOf("b1"), subtree(nodes, "b1").map { it.id })
        assertEquals(emptyList(), subtree(nodes, "gone"))
    }

    @Test fun findBookmarkByUrlPicksTheNewestOfSeveralByTheUrlKey() {
        val saved = listOf(
            bookmark("old", url = "https://Example.com", createdAt = 1),
            bookmark("new", "work", url = "https://example.com/#top", createdAt = 5),
            bookmark("other", url = "https://example.org/", createdAt = 9),
            folder("work"),
        )
        assertEquals("new", findBookmarkByUrl(saved, "https://example.com/")?.id)
        assertNull(findBookmarkByUrl(saved, "https://example.net/"))
    }

    @Test fun moveToIndexMovesForwardAndBackward() {
        val ids = listOf("a", "b", "c", "d")
        assertEquals(listOf("b", "c", "a", "d"), moveToIndex(ids, "a", 2))
        assertEquals(listOf("d", "a", "b", "c"), moveToIndex(ids, "d", 0))
    }

    @Test fun moveToIndexClampsToTheList() {
        val ids = listOf("a", "b", "c")
        assertEquals(listOf("b", "c", "a"), moveToIndex(ids, "a", 99))
        assertEquals(listOf("c", "a", "b"), moveToIndex(ids, "c", -5))
    }

    @Test fun moveToIndexToTheSamePlaceOrOfAnUnknownIdLeavesTheList() {
        val ids = listOf("a", "b", "c")
        assertEquals(ids, moveToIndex(ids, "b", 1))
        assertEquals(ids, moveToIndex(ids, "gone", 0))
    }

    @Test fun reorderedRenumbersOnlyTheSiblingsWhosePositionChanges() {
        val rows = listOf(bookmark("a", position = 0), bookmark("b", position = 1), bookmark("c", position = 2), bookmark("d", position = 3))
        val changed = reordered(rows, "d", 1)
        assertEquals(mapOf("d" to 1, "b" to 2, "c" to 3), changed.associate { it.id to it.position })
    }

    @Test fun reorderedNormalisesTiedPositionsInCreationThenIdOrder() {
        val rows = listOf(bookmark("z", createdAt = 1), bookmark("y", createdAt = 2), bookmark("x", createdAt = 1))
        // display order: x, z, y; moving y to index 0 gives y, x, z; y already holds position 0
        assertEquals(mapOf("x" to 1, "z" to 2), reordered(rows, "y", 0).associate { it.id to it.position })
    }

    @Test fun reorderedNeverTouchesAnotherParent() {
        val changed = reordered(nodes, "b2", 0)
        assertEquals(setOf("docs", "b2"), changed.map { it.id }.toSet())
        assertTrue(changed.all { it.parentId == "work" })
    }

    @Test fun reorderedIsEmptyForAnUnknownIdAnOrphanOrNoChange() {
        assertEquals(emptyList(), reordered(nodes, "gone", 0))
        assertEquals(emptyList(), reordered(nodes, "b1", 2))
        assertEquals(emptyList(), reordered(listOf(bookmark("o", "gone"), bookmark("b1", position = 1)), "o", 1))
    }
}
