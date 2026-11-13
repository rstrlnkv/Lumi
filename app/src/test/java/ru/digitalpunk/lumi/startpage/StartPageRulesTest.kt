package ru.digitalpunk.lumi.startpage

import ru.digitalpunk.lumi.data.library.BookmarkNodeEntity
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import ru.digitalpunk.lumi.startpage.TileAction.DeleteBookmark
import ru.digitalpunk.lumi.startpage.TileAction.DeleteFolder
import ru.digitalpunk.lumi.startpage.TileAction.Edit
import ru.digitalpunk.lumi.startpage.TileAction.OpenFolder
import ru.digitalpunk.lumi.startpage.TileAction.OpenInNewPrivateTab
import ru.digitalpunk.lumi.startpage.TileAction.OpenInNewTab
import ru.digitalpunk.lumi.startpage.TileAction.OpenInPrivateTab
import ru.digitalpunk.lumi.startpage.TileAction.Rename
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

private fun folder(id: String, parent: String = ROOT, position: Int = 0, title: String = id) =
    BookmarkNodeEntity(id, parent, BookmarkNodeEntity.FOLDER, title, null, position, 0)

private fun bookmark(id: String, parent: String, position: Int) =
    BookmarkNodeEntity(id, parent, BookmarkNodeEntity.BOOKMARK, "Title $id", "https://$id.com/", position, 0)

class StartPageRulesTest {
    // favorites: b1(2), travel(0), b2(1); travel: maps(0), b3(1), b4(2); maps: b5(0)
    private val nodes = listOf(
        folder(FAVORITES, title = ""),
        bookmark("b1", FAVORITES, 2),
        folder("travel", FAVORITES, 0, "Travel"),
        bookmark("b2", FAVORITES, 1),
        folder("maps", "travel", 0),
        bookmark("b3", "travel", 1),
        bookmark("b4", "travel", 2),
        bookmark("b5", "maps", 0),
        folder("other", ROOT, 1),
    )

    @Test fun theStartPageShowsOnABlankPageThatIsNotLoadingOrCrashed() {
        assertTrue(startPageVisible("about:blank", loading = false, crashed = false))
        assertTrue(startPageVisible("ABOUT:BLANK", loading = false, crashed = false))
        assertFalse(startPageVisible("about:blank", loading = true, crashed = false))
        assertFalse(startPageVisible("about:blank", loading = false, crashed = true))
        assertFalse(startPageVisible("about:blank?x", loading = false, crashed = false))
        assertFalse(startPageVisible("https://a.com/", loading = false, crashed = false))
    }

    @Test fun aNewTabFocusesTheFieldOnlyWhenTheStartFolderIsEmpty() {
        assertTrue(focusNewTab(0))
        assertFalse(focusNewTab(1))
        assertFalse(focusNewTab(24))
    }

    @Test fun navigationGoesIntoAndUpOneLevelAndUpOfTheTopStaysThere() {
        assertEquals("favorites", currentFolder(emptyList(), FAVORITES))
        val inside = navInto(navInto(emptyList(), "travel"), "maps")
        assertEquals(listOf("travel", "maps"), inside)
        assertEquals("maps", currentFolder(inside, FAVORITES))
        assertEquals(listOf("travel"), navUp(inside))
        assertEquals(emptyList(), navUp(emptyList()))
    }

    @Test fun reconcileKeepsAValidPathAndPopsADeletedOpenFolder() {
        assertEquals(listOf("travel", "maps"), reconcile(listOf("travel", "maps"), nodes, FAVORITES))
        assertEquals(listOf("travel"), reconcile(listOf("travel", "maps"), nodes.filter { it.id != "maps" }, FAVORITES))
    }

    @Test fun reconcileResetsWhenTheStartFolderChangesOrTheFolderMovedOut() {
        assertEquals(emptyList(), reconcile(listOf("travel"), nodes, "other"))
        val moved = nodes.map { if (it.id == "travel") it.copy(parentId = "other") else it }
        assertEquals(emptyList(), reconcile(listOf("travel", "maps"), moved, FAVORITES))
    }

    @Test fun backGoesUpOnlyOnAVisibleSubfolderWhileNotEditingAndNotCovered() {
        assertTrue(backGoesUp(visible = true, stack = listOf("travel"), editing = false, covered = false))
        assertFalse(backGoesUp(visible = false, stack = listOf("travel"), editing = false, covered = false))
        assertFalse(backGoesUp(visible = true, stack = emptyList(), editing = false, covered = false))
        assertFalse(backGoesUp(visible = true, stack = listOf("travel"), editing = true, covered = false))
        assertFalse(backGoesUp(visible = true, stack = listOf("travel"), editing = false, covered = true))
    }

    @Test fun portraitHasFourColumnsAndLandscapeEight() {
        assertEquals(4, gridColumns(landscape = false))
        assertEquals(8, gridColumns(landscape = true))
    }

    @Test fun tilesFollowTheFolderOrderAndCountNestedBookmarks() {
        assertEquals(
            listOf(Tile.Folder("travel", "Travel", 3), Tile.Bookmark("b2", "Title b2", "https://b2.com/"), Tile.Bookmark("b1", "Title b1", "https://b1.com/")),
            tiles(nodes, FAVORITES, "Favorites"),
        )
        assertEquals(listOf(Tile.Folder("maps", "maps", 1), Tile.Bookmark("b3", "Title b3", "https://b3.com/"), Tile.Bookmark("b4", "Title b4", "https://b4.com/")), tiles(nodes, "travel", "Favorites"))
    }

    @Test fun aNeverRenamedFavoritesFolderTileUsesTheLocalizedName() {
        val inRoot = tiles(nodes, ROOT, "Избранное")
        assertEquals(Tile.Folder(FAVORITES, "Избранное", 5), inRoot.first())
    }

    @Test fun theMenuOfABookmarkInANormalTabOffersNewTabPrivateTabEditAndDelete() =
        assertEquals(listOf(OpenInNewTab, OpenInPrivateTab, Edit, DeleteBookmark), tileMenu(Tile.Bookmark("b", "t", "u"), inPrivateTab = false))

    @Test fun theMenuOfABookmarkInAPrivateTabOffersOnlyANewPrivateTabEditAndDelete() =
        assertEquals(listOf(OpenInNewPrivateTab, Edit, DeleteBookmark), tileMenu(Tile.Bookmark("b", "t", "u"), inPrivateTab = true))

    @Test fun theMenuOfAFolderOffersOpenRenameAndDeleteButFavoritesIsNeverDeleted() {
        assertEquals(listOf(OpenFolder, Rename, DeleteFolder), tileMenu(Tile.Folder("travel", "Travel", 3), inPrivateTab = false))
        assertEquals(listOf(OpenFolder, Rename, DeleteFolder), tileMenu(Tile.Folder("travel", "Travel", 3), inPrivateTab = true))
        assertEquals(listOf(OpenFolder, Rename), tileMenu(Tile.Folder(FAVORITES, "Favorites", 4), inPrivateTab = false))
    }
}
