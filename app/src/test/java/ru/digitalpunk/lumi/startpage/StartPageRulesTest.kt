package ru.digitalpunk.lumi.startpage

import ru.digitalpunk.lumi.data.library.BookmarkNodeEntity
import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import ru.digitalpunk.lumi.data.library.LibraryIds.ROOT
import ru.digitalpunk.lumi.startpage.TileAction.DeleteBookmark
import ru.digitalpunk.lumi.startpage.TileAction.DeleteFolder
import ru.digitalpunk.lumi.startpage.TileAction.Edit
import ru.digitalpunk.lumi.startpage.TileAction.EditOrder
import ru.digitalpunk.lumi.startpage.TileAction.OpenFolder
import ru.digitalpunk.lumi.startpage.TileAction.OpenInNewPrivateTab
import ru.digitalpunk.lumi.startpage.TileAction.OpenInNewTab
import ru.digitalpunk.lumi.startpage.TileAction.OpenInPrivateTab
import ru.digitalpunk.lumi.startpage.TileAction.Rename
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
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

    @Test fun theMenuOfABookmarkInANormalTabOffersNewTabPrivateTabEditOrderAndDelete() =
        assertEquals(listOf(OpenInNewTab, OpenInPrivateTab, Edit, EditOrder, DeleteBookmark), tileMenu(Tile.Bookmark("b", "t", "u"), inPrivateTab = false))

    @Test fun theMenuOfABookmarkInAPrivateTabOffersOnlyANewPrivateTabEditOrderAndDelete() =
        assertEquals(listOf(OpenInNewPrivateTab, Edit, EditOrder, DeleteBookmark), tileMenu(Tile.Bookmark("b", "t", "u"), inPrivateTab = true))

    @Test fun theMenuOfAFolderOffersOpenRenameOrderAndDeleteButFavoritesIsNeverDeleted() {
        assertEquals(listOf(OpenFolder, Rename, EditOrder, DeleteFolder), tileMenu(Tile.Folder("travel", "Travel", 3), inPrivateTab = false))
        assertEquals(listOf(OpenFolder, Rename, EditOrder, DeleteFolder), tileMenu(Tile.Folder("travel", "Travel", 3), inPrivateTab = true))
        assertEquals(listOf(OpenFolder, Rename, EditOrder), tileMenu(Tile.Folder(FAVORITES, "Favorites", 4), inPrivateTab = false))
    }

    @Test fun deleteActionIsDeleteBookmarkForABookmarkDeleteFolderForAFolderAndNullForFavorites() {
        assertEquals(DeleteBookmark, deleteAction(Tile.Bookmark("b", "t", "u")))
        assertEquals(DeleteFolder, deleteAction(Tile.Folder("travel", "Travel", 3)))
        assertNull(deleteAction(Tile.Folder(FAVORITES, "Favorites", 4)))
    }

    @Test fun aMoveWithinTheSlopWaitsWhateverTheTime() {
        assertEquals(Press.Wait, pressOutcome(false, 10, 3f, 400, 8f))
        assertEquals(Press.Wait, pressOutcome(false, 900, 8f, 400, 8f))
        assertEquals(Press.Wait, pressOutcome(true, 900, 8f, 400, 8f))
    }

    @Test fun aMovePastTheSlopBeforeTheLongPressTimeoutIsAScroll() =
        assertEquals(Press.Scroll, pressOutcome(false, 399, 9f, 400, 8f))

    @Test fun aMovePastTheSlopAtOrAfterTheLongPressTimeoutIsADrag() {
        assertEquals(Press.Drag, pressOutcome(false, 400, 9f, 400, 8f))
        assertEquals(Press.Drag, pressOutcome(false, 1500, 40f, 400, 8f))
    }

    @Test fun inEditModeAMovePastTheSlopIsADragAtOnce() =
        assertEquals(Press.Drag, pressOutcome(true, 0, 9f, 400, 8f))

    // Slots of a 2-column grid, 100 x 100 each, no gap: a (0,0), b (100,0), c (0,100, a folder), d (100,100)
    private val slots = listOf(
        TileSlot("a", 0, 0f, 0f, 100f, 100f, folder = false),
        TileSlot("b", 1, 100f, 0f, 100f, 100f, folder = false),
        TileSlot("c", 2, 0f, 100f, 100f, 100f, folder = true),
        TileSlot("d", 3, 100f, 100f, 100f, 100f, folder = false),
    )

    @Test fun overABookmarkSlotTheTargetIsItsIndex() =
        assertEquals(DragTarget.Move(1), dragTarget(slots, "a", 150f, 50f))

    @Test fun outsideEverySlotOrOverTheDraggedSlotTheTargetIsStay() {
        assertEquals(DragTarget.Stay, dragTarget(slots, "a", 250f, 50f))
        assertEquals(DragTarget.Stay, dragTarget(slots, "a", 50f, 50f))
    }

    @Test fun inTheMiddleSixtyPercentOfAFolderSlotTheTargetIsIntoAndOnTheRingItIsMove() {
        assertEquals(DragTarget.Into("c"), dragTarget(slots, "a", 50f, 150f))
        assertEquals(DragTarget.Move(2), dragTarget(slots, "a", 10f, 150f))
        assertEquals(DragTarget.Move(2), dragTarget(slots, "a", 50f, 195f))
    }

    @Test fun theCoreOfAFolderStartsAtTwentyPercentAndEndsBeforeEightyPercent() {
        assertEquals(DragTarget.Into("c"), dragTarget(slots, "a", 20f, 120f))
        assertEquals(DragTarget.Move(2), dragTarget(slots, "a", 80f, 120f))
        assertEquals(DragTarget.Move(2), dragTarget(slots, "a", 50f, 119.9f))
    }

    @Test fun aFolderThatMayNotReceiveIsMoveEverywhere() {
        val closed = slots.map { if (it.id == "c") it.copy(folder = false) else it }
        assertEquals(DragTarget.Move(2), dragTarget(closed, "a", 50f, 150f))
    }

    @Test fun aFolderIsHighlightedAfterSevenHundredMillisecondsOverIt() {
        assertFalse(folderHighlighted(null, 5000))
        assertFalse(folderHighlighted(1000, 1699))
        assertTrue(folderHighlighted(1000, 1700))
    }

    @Test fun reconcileOrderDropsGoneIdsAndAppendsNewOnes() {
        assertEquals(listOf("c", "a", "x"), reconcileOrder(listOf("c", "b", "a"), listOf("a", "c", "x")))
        assertEquals(listOf("c", "a"), reconcileOrder(listOf("c", "a"), listOf("a", "c")))
    }

    @Test fun edgeScrollIsNegativeInTheTopZonePositiveInTheBottomZoneAndZeroBetween() {
        assertEquals(-10f, edgeScroll(0f, 0f, 1000f, 100f, 10f))
        assertEquals(10f, edgeScroll(1000f, 0f, 1000f, 100f, 10f))
        assertEquals(0f, edgeScroll(500f, 0f, 1000f, 100f, 10f))
    }

    @Test fun edgeScrollGrowsWithTheDepthIntoTheZone() {
        assertTrue(edgeScroll(20f, 0f, 1000f, 100f, 10f) < edgeScroll(80f, 0f, 1000f, 100f, 10f))
        assertEquals(-5f, edgeScroll(50f, 0f, 1000f, 100f, 10f))
        assertEquals(-10f, edgeScroll(-40f, 0f, 1000f, 100f, 10f))
    }

    @Test fun edgeScrollWithAZoneLargerThanTheViewportStaysFinite() {
        val up = edgeScroll(10f, 0f, 100f, 500f, 10f)
        val down = edgeScroll(90f, 0f, 100f, 500f, 10f)
        assertTrue(up < 0f && down > 0f && up.isFinite() && down.isFinite())
        assertEquals(0f, edgeScroll(10f, 0f, 100f, 0f, 10f))
    }

    @Test fun shiftedIndexStaysInTheListOrIsNull() {
        assertNull(shiftedIndex(0, 4, -1))
        assertNull(shiftedIndex(3, 4, 1))
        assertEquals(2, shiftedIndex(1, 4, 1))
        assertEquals(0, shiftedIndex(1, 4, -1))
    }
}
