package ru.digitalpunk.lumi.data.library

import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import kotlin.test.Test
import kotlin.test.assertEquals

class EffectiveStartFolderTest {
    private val nodes = listOf(
        folder(FAVORITES, position = 0, title = ""),
        folder("travel", position = 1),
        folder("maps", "travel", 0),
        bookmark("b1", "travel", 1),
    )

    @Test fun anExistingFolderIsKept() = assertEquals("travel", effectiveStartFolder(nodes, "travel"))

    @Test fun aNestedFolderIsKept() = assertEquals("maps", effectiveStartFolder(nodes, "maps"))

    @Test fun aDeletedFolderFallsBackToFavorites() = assertEquals(FAVORITES, effectiveStartFolder(nodes, "gone"))

    @Test fun aFolderWhoseAncestorWasDeletedFallsBackToFavorites() =
        assertEquals(FAVORITES, effectiveStartFolder(nodes.filter { it.parentId != "travel" && it.id != "travel" }, "maps"))

    @Test fun theRootABookmarkAndAnEmptyStoreFallBackToFavorites() {
        assertEquals(FAVORITES, effectiveStartFolder(nodes, ROOT))
        assertEquals(FAVORITES, effectiveStartFolder(nodes, "b1"))
        assertEquals(FAVORITES, effectiveStartFolder(emptyList(), "travel"))
    }
}
