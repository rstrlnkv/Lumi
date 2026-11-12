package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.PanZoomController
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** [PageTouchInfo.fromFlags] copies GeckoView's flag values; these tests read the real constants so a renumbering shows. */
class PageTouchGeckoConstantsTest {
    @Test fun handledMeansGeckosHandledAndNothingElse() {
        assertTrue(PageTouchInfo.fromFlags(PanZoomController.INPUT_RESULT_HANDLED, 0, 0).browserHandles)
        listOf(
            PanZoomController.INPUT_RESULT_UNHANDLED,
            PanZoomController.INPUT_RESULT_HANDLED_CONTENT,
            PanZoomController.INPUT_RESULT_IGNORED,
        ).forEach { assertFalse(PageTouchInfo.fromFlags(it, 0, 0).browserHandles, "result $it") }
    }

    @Test fun scrollFlagsMatchGecko() {
        val top = PageTouchInfo.fromFlags(1, PanZoomController.SCROLLABLE_FLAG_TOP, 0)
        assertEquals(listOf(true, false), listOf(top.canScrollUp, top.canScrollDown))
        val bottom = PageTouchInfo.fromFlags(1, PanZoomController.SCROLLABLE_FLAG_BOTTOM, 0)
        assertEquals(listOf(false, true), listOf(bottom.canScrollUp, bottom.canScrollDown))
        val sides = PageTouchInfo.fromFlags(1, PanZoomController.SCROLLABLE_FLAG_LEFT or PanZoomController.SCROLLABLE_FLAG_RIGHT, 0)
        assertEquals(listOf(false, false), listOf(sides.canScrollUp, sides.canScrollDown))
    }

    @Test fun overscrollFlagsMatchGecko() {
        assertTrue(PageTouchInfo.fromFlags(1, 0, PanZoomController.OVERSCROLL_FLAG_VERTICAL).canOverscrollVertically)
        assertFalse(PageTouchInfo.fromFlags(1, 0, PanZoomController.OVERSCROLL_FLAG_HORIZONTAL).canOverscrollVertically)
    }
}
