package ru.digitalpunk.lumi.ui

import ru.digitalpunk.lumi.engine.TabId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class NewTabNoticeTest {
    private val first = NewTabNotice(TabId("n1"), private = false)
    private val second = NewTabNotice(TabId("n2"), private = false)

    @Test fun clearedByTheSameNoticeClears() {
        assertNull(first.clearedBy(first))
    }

    @Test fun lateClearOfAnOlderNoticeKeepsTheNewest() {
        assertEquals(second, second.clearedBy(first))
    }

    @Test fun clearingNullStaysNull() {
        assertNull((null as NewTabNotice?).clearedBy(first))
    }
}
