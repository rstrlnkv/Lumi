package ru.digitalpunk.lumi.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import ru.digitalpunk.lumi.gesture.BarCollapse

class FieldTapTest {
    @Test fun aTapToEditWhileTheBarIsNotExpandedOnlyExpandsIt() =
        assertEquals(FieldTap.Expand, fieldTap(wantEditing = true, barExpanded = false))

    @Test fun aTapToEditOnTheExpandedBarStartsEditing() =
        assertEquals(FieldTap.Set, fieldTap(wantEditing = true, barExpanded = true))

    @Test fun leavingEditModeIsNeverBlockedInEitherBarState() {
        assertEquals(FieldTap.Set, fieldTap(wantEditing = false, barExpanded = true))
        assertEquals(FieldTap.Set, fieldTap(wantEditing = false, barExpanded = false))
    }

    @Test fun aTapOnePixelIntoTheSlideOnlyExpands() {
        val bar = BarCollapse(maxHidden = 300f)
        bar.snapTo(0.01f)
        assertEquals(FieldTap.Expand, fieldTap(wantEditing = true, barExpanded = bar.isExpanded))
    }
}
