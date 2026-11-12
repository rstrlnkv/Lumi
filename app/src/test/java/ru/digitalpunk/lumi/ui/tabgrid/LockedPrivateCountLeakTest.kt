package ru.digitalpunk.lumi.ui.tabgrid

import ru.digitalpunk.lumi.engine.PRIVATE_GROUP
import ru.digitalpunk.lumi.engine.TabId
import ru.digitalpunk.lumi.engine.TabInfo
import ru.digitalpunk.lumi.engine.TabsState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Back on the Private chip while the lock is on (a normal tab selected, three private tabs hidden): no count shows anywhere. */
class LockedPrivateCountLeakTest {
    private val locked = TabsState(
        tabs = listOf(TabInfo(TabId("a"), "", "", false, false)),
        selectedId = TabId("a"),
        currentGroup = PRIVATE_GROUP,
        privateCount = 3,
        privateLocked = true,
    )

    @Test fun privateChipCountIsNullWhileLockedOnThePrivateChip() = assertNull(privateChipCount(locked))

    @Test fun privateChipModelCarriesZeroNotTheHiddenCount() =
        assertEquals(0, chips(locked).first { it.kind == ChipKind.Private }.count)

    @Test fun toolbarAndTabsButtonCountIsTheSelectedTabsGroupNotTheHiddenCount() = assertEquals(1, currentGroupTabCount(locked))

    @Test fun unlockedStateShowsTheRealCount() {
        val open = locked.copy(privateLocked = false)
        assertEquals(3, privateChipCount(open))
    }
}
