package ru.digitalpunk.lumi.startpage

import ru.digitalpunk.lumi.data.library.BookmarkNodeEntity
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import kotlin.test.Test
import kotlin.test.assertEquals

private fun dir(id: String, parent: String = ROOT, position: Int = 0) =
    BookmarkNodeEntity(id, parent, BookmarkNodeEntity.FOLDER, id, null, position, 0)

private fun mark(id: String, parent: String, position: Int = 0) =
    BookmarkNodeEntity(id, parent, BookmarkNodeEntity.BOOKMARK, id, "https://$id.com/", position, 0)

/** Inputs the happy-path tests do not feed: a bookmark id in the stack, repeats, a missing folder, a cyclic file. */
class StartPageEdgeTest {
    private val nodes = listOf(dir(FAVORITES), mark("b1", FAVORITES), dir("a", FAVORITES, 1), dir("c", "a"))

    @Test fun aBookmarkIdInTheStackIsNotAFolderAndEndsTheChain() {
        assertEquals(emptyList(), reconcile(listOf("b1"), nodes, FAVORITES))
        assertEquals(listOf("a"), reconcile(listOf("a", "b1"), nodes, FAVORITES))
    }

    @Test fun theSameFolderTwiceInARowKeepsOnlyTheFirst() =
        assertEquals(listOf("a"), reconcile(listOf("a", "a"), nodes, FAVORITES))

    @Test fun anEmptyStackStaysEmptyAndAnEmptyStoreClearsTheStack() {
        assertEquals(emptyList(), reconcile(emptyList(), nodes, FAVORITES))
        assertEquals(emptyList(), reconcile(listOf("a", "c"), emptyList(), FAVORITES))
    }

    @Test fun tilesOfAMissingFolderAreEmptyAndSoTheNewTabFocusesTheField() {
        val t = tiles(nodes, "gone", "Favorites")
        assertEquals(0, t.size)
        assertEquals(true, focusNewTab(t.size))
    }

    @Test fun aCyclicFileDoesNotHangTheTileCount() {
        val cyclic = listOf(dir("x", "y"), dir("y", "x"), mark("m", "x"))
        val t = tiles(cyclic, "x", "Favorites")
        assertEquals(listOf("y", "m"), t.map { it.id }.sortedByDescending { it })
    }
}
