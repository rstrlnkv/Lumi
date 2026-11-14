package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class MenuGroupShapeTest {
    private val outer = RoundedCornerShape(16.dp)
    private val joint = CornerSize(6.dp)
    private val density = Density(1f)
    private val box = Size(100f, 100f)

    /** The corners as topStart, topEnd, bottomEnd, bottomStart in px. */
    private fun corners(slot: GroupSlot): List<Float> {
        val s = groupShape(slot, outer, joint) as CornerBasedShape
        return listOf(s.topStart, s.topEnd, s.bottomEnd, s.bottomStart).map { it.toPx(box, density) }
    }

    @Test fun slotFollowsThePositionInTheStack() {
        assertEquals(GroupSlot.Only, groupSlot(0, 1))
        assertEquals(GroupSlot.First, groupSlot(0, 3))
        assertEquals(GroupSlot.Middle, groupSlot(1, 3))
        assertEquals(GroupSlot.Last, groupSlot(2, 3))
        assertEquals(GroupSlot.First, groupSlot(0, 2))
        assertEquals(GroupSlot.Last, groupSlot(1, 2))
    }

    @Test fun everyInnerGroupOfALongStackIsMiddle() {
        assertEquals(
            listOf(GroupSlot.First, GroupSlot.Middle, GroupSlot.Middle, GroupSlot.Last),
            (0..3).map { groupSlot(it, 4) },
        )
    }

    @Test fun aLoneGroupIsRoundedOnAllCorners() = assertEquals(listOf(16f, 16f, 16f, 16f), corners(GroupSlot.Only))

    @Test fun theFirstGroupHasSmallBottomCorners() = assertEquals(listOf(16f, 16f, 6f, 6f), corners(GroupSlot.First))

    @Test fun aMiddleGroupHasSmallCornersEverywhere() = assertEquals(listOf(6f, 6f, 6f, 6f), corners(GroupSlot.Middle))

    @Test fun theLastGroupHasSmallTopCorners() = assertEquals(listOf(6f, 6f, 16f, 16f), corners(GroupSlot.Last))
}
