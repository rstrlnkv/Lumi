package ru.digitalpunk.lumi.startpage

import ru.digitalpunk.lumi.data.library.LibraryIds.FAVORITES
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Inputs the drag tests do not feed: empty grids, offset non-square cells, gaps, edges of each axis, odd clocks. */
class StartPageDragEdgeTest {
    // A folder cell that is neither square nor at the origin: x 300..400 (w 100), y 500..700 (h 200); core x 320..380, y 540..660
    private val folderCell = TileSlot("f", 5, 300f, 500f, 100f, 200f, folder = true)
    private val other = TileSlot("o", 4, 100f, 500f, 100f, 200f, folder = false)
    private val grid = listOf(other, folderCell)

    @Test fun anEmptyGridAlwaysStays() = assertEquals(DragTarget.Stay, dragTarget(emptyList(), "a", 10f, 10f))

    @Test fun aDraggedIdThatIsNotInTheGridStillGetsATargetFromTheCellUnderTheFinger() =
        assertEquals(DragTarget.Move(4), dragTarget(grid, "gone", 150f, 600f))

    @Test fun aFingerInTheGapBetweenTwoCellsStays() = assertEquals(DragTarget.Stay, dragTarget(grid, "x", 250f, 600f))

    @Test fun aFingerLeftOfAbovePastTheGridOrAtNegativeCoordinatesStays() {
        assertEquals(DragTarget.Stay, dragTarget(grid, "x", -5f, -5f))
        assertEquals(DragTarget.Stay, dragTarget(grid, "x", 150f, 499.9f))
        assertEquals(DragTarget.Stay, dragTarget(grid, "x", 150f, 700f))
        assertEquals(DragTarget.Stay, dragTarget(grid, "x", 400f, 600f))
    }

    @Test fun aNaNFingerStaysInsteadOfPickingACell() = assertEquals(DragTarget.Stay, dragTarget(grid, "x", Float.NaN, Float.NaN))

    @Test fun theFolderCoreUsesTheCellsOwnOffsetOnTheXAxis() {
        assertEquals(DragTarget.Move(5), dragTarget(grid, "x", 319.9f, 600f))
        assertEquals(DragTarget.Into("f"), dragTarget(grid, "x", 320f, 600f))
        assertEquals(DragTarget.Into("f"), dragTarget(grid, "x", 379.9f, 600f))
        assertEquals(DragTarget.Move(5), dragTarget(grid, "x", 380f, 600f))
    }

    @Test fun theFolderCoreUsesTheCellsOwnOffsetAndHeightOnTheYAxis() {
        assertEquals(DragTarget.Move(5), dragTarget(grid, "x", 350f, 539.9f))
        assertEquals(DragTarget.Into("f"), dragTarget(grid, "x", 350f, 540f))
        assertEquals(DragTarget.Into("f"), dragTarget(grid, "x", 350f, 659.9f))
        assertEquals(DragTarget.Move(5), dragTarget(grid, "x", 350f, 660f))
    }

    @Test fun theCornersOfAFolderCellAreOnTheRingAndTheLeftTopCornerBelongsToIt() {
        assertEquals(DragTarget.Move(5), dragTarget(grid, "x", 300f, 500f))
        assertEquals(DragTarget.Move(5), dragTarget(grid, "x", 399.9f, 699.9f))
    }

    @Test fun aNonFolderCellNeverGivesIntoEvenAtItsCentre() = assertEquals(DragTarget.Move(4), dragTarget(grid, "x", 150f, 600f))

    @Test fun theDraggedFoldersOwnCellStaysEvenAtItsCore() = assertEquals(DragTarget.Stay, dragTarget(grid, "f", 350f, 600f))

    @Test fun twoCellsThatOverlapGiveTheFirstOne() {
        val a = TileSlot("a", 0, 0f, 0f, 100f, 100f, folder = false)
        val b = TileSlot("b", 1, 50f, 0f, 100f, 100f, folder = true)
        assertEquals(DragTarget.Move(0), dragTarget(listOf(a, b), "x", 75f, 50f))
        assertEquals(DragTarget.Into("b"), dragTarget(listOf(b, a), "x", 100f, 50f))
    }

    @Test fun aZeroSizedCellIsNeverHit() =
        assertEquals(DragTarget.Stay, dragTarget(listOf(TileSlot("z", 0, 10f, 10f, 0f, 0f, folder = true)), "x", 10f, 10f))

    @Test fun aFolderIsNotHighlightedWhenTheClockGoesBackwards() {
        assertFalse(folderHighlighted(5000, 4000))
        assertFalse(folderHighlighted(5000, 5000))
        assertFalse(folderHighlighted(0, 699))
        assertTrue(folderHighlighted(0, 700))
    }

    @Test fun aPressOfAnyLengthWithoutMovementNeverBecomesADragOrScroll() {
        assertEquals(Press.Wait, pressOutcome(false, Long.MAX_VALUE, 0f, 400, 8f))
        assertEquals(Press.Wait, pressOutcome(true, 0, 0f, 400, 8f))
    }

    @Test fun aNegativeHeldTimeFromAClockStepNeverMakesALongPress() =
        assertEquals(Press.Scroll, pressOutcome(false, -50, 20f, 400, 8f))

    @Test fun aNaNMoveNeverStartsADragOutsideEditMode() = assertEquals(Press.Scroll, pressOutcome(false, 10, Float.NaN, 400, 8f))

    @Test fun reconcileOrderOfEmptyInputsIsEmpty() {
        assertEquals(emptyList(), reconcileOrder(emptyList(), emptyList()))
        assertEquals(emptyList(), reconcileOrder(listOf("a", "b"), emptyList()))
    }

    @Test fun reconcileOrderWithNoShownOrderIsTheStoredOrder() = assertEquals(listOf("a", "b"), reconcileOrder(emptyList(), listOf("a", "b")))

    @Test fun reconcileOrderKeepsTheShownOrderAndAppendsSeveralNewIdsInStoredOrder() =
        assertEquals(listOf("c", "a", "x", "y"), reconcileOrder(listOf("c", "a"), listOf("a", "x", "c", "y")))

    @Test fun reconcileOrderOfASingleTileFolderAfterItsOnlyTileIsGoneIsEmpty() =
        assertEquals(emptyList(), reconcileOrder(listOf("only"), emptyList()))

    @Test fun reconcileOrderNeverReturnsAnIdTwiceWhenInputsAreUnique() {
        val out = reconcileOrder(listOf("b", "a"), listOf("a", "b", "c"))
        assertEquals(out.size, out.toSet().size)
    }

    @Test fun edgeScrollIsHalfwayDeepInTheBottomZoneAndUsesTheTopOffset() {
        assertEquals(5f, edgeScroll(950f, 0f, 1000f, 100f, 10f))
        assertEquals(-5f, edgeScroll(250f, 200f, 1200f, 100f, 10f))
        assertEquals(5f, edgeScroll(1150f, 200f, 1200f, 100f, 10f))
    }

    @Test fun edgeScrollIsZeroExactlyAtTheInnerEdgeOfEachZoneAndMovesOnePixelInside() {
        assertEquals(0f, edgeScroll(100f, 0f, 1000f, 100f, 10f))
        assertEquals(0f, edgeScroll(900f, 0f, 1000f, 100f, 10f))
        assertTrue(edgeScroll(99f, 0f, 1000f, 100f, 10f) < 0f)
        assertTrue(edgeScroll(901f, 0f, 1000f, 100f, 10f) > 0f)
    }

    @Test fun edgeScrollWithAZoneExactlyHalfTheViewportIsZeroAtTheMiddle() = assertEquals(0f, edgeScroll(50f, 0f, 100f, 50f, 10f))

    @Test fun edgeScrollOfAnEmptyOrInvertedViewportIsZero() {
        assertEquals(0f, edgeScroll(5f, 10f, 10f, 72f, 16f))
        assertEquals(0f, edgeScroll(5f, 100f, 0f, 72f, 16f))
    }

    @Test fun edgeScrollForANaNFingerIsZero() = assertEquals(0f, edgeScroll(Float.NaN, 0f, 1000f, 100f, 10f))

    @Test fun shiftedIndexOfAnEmptyOrSingleTileListIsNullInBothDirections() {
        assertNull(shiftedIndex(0, 0, 1))
        assertNull(shiftedIndex(0, 0, -1))
        assertNull(shiftedIndex(0, 1, 1))
        assertNull(shiftedIndex(0, 1, -1))
    }

    @Test fun shiftedIndexReachesTheFirstAndLastPlacesButNotOnePast() {
        assertEquals(0, shiftedIndex(1, 3, -1))
        assertEquals(2, shiftedIndex(1, 3, 1))
        assertNull(shiftedIndex(2, 3, 1))
        assertNull(shiftedIndex(0, 3, -1))
    }

    @Test fun theFavoritesMenuInAPrivateTabOffersOpenRenameAndOrderButNoDelete() {
        assertEquals(
            listOf(TileAction.OpenFolder, TileAction.Rename, TileAction.EditOrder),
            tileMenu(Tile.Folder(FAVORITES, "F", 0), inPrivateTab = true),
        )
    }

    @Test fun everyMenuOffersEditOrderSoTheModeCanBeEnteredFromAnyTile() {
        for (private in listOf(true, false)) {
            assertTrue(TileAction.EditOrder in tileMenu(Tile.Bookmark("b", "t", "u"), private))
            assertTrue(TileAction.EditOrder in tileMenu(Tile.Folder("f", "F", 0), private))
        }
    }
}
