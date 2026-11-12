package ru.digitalpunk.lumi.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PageTouchInfoTest {
    private val handled = 1 // INPUT_RESULT_HANDLED
    private val handledContent = 2 // INPUT_RESULT_HANDLED_CONTENT
    private val top = 1 // SCROLLABLE_FLAG_TOP
    private val bottom = 4 // SCROLLABLE_FLAG_BOTTOM
    private val vertical = 2 // OVERSCROLL_FLAG_VERTICAL

    @Test fun handledByBrowserIsBrowserHandles() = assertTrue(PageTouchInfo.fromFlags(handled, 0, 0).browserHandles)

    @Test fun handledByContentIsNotBrowserHandles() = assertFalse(PageTouchInfo.fromFlags(handledContent, 0, 0).browserHandles)

    @Test fun pullToRefreshWhenHandledAtTopWithVerticalOverscroll() =
        assertTrue(PageTouchInfo.fromFlags(handled, bottom, vertical).canPullToRefresh)

    @Test fun noPullToRefreshWhileCanStillScrollUp() =
        assertFalse(PageTouchInfo.fromFlags(handled, top or bottom, vertical).canPullToRefresh)

    @Test fun noPullToRefreshWithoutVerticalOverscroll() =
        assertFalse(PageTouchInfo.fromFlags(handled, bottom, 0).canPullToRefresh)

    @Test fun noPullToRefreshWhenBrowserDoesNotHandle() =
        assertFalse(PageTouchInfo.fromFlags(handledContent, bottom, vertical).canPullToRefresh)

    @Test fun canScrollDownOnlyWithBottomFlag() {
        assertTrue(PageTouchInfo.fromFlags(handled, bottom, 0).canScrollDown)
        assertFalse(PageTouchInfo.fromFlags(handled, top, 0).canScrollDown)
    }

    @Test fun canScrollUpOnlyWithTopFlag() {
        assertEquals(true, PageTouchInfo.fromFlags(handled, top, 0).canScrollUp)
        assertEquals(false, PageTouchInfo.fromFlags(handled, bottom, 0).canScrollUp)
    }
}
