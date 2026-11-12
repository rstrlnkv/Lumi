package ru.digitalpunk.lumi.privacy

import ru.digitalpunk.lumi.engine.GroupId
import ru.digitalpunk.lumi.engine.PRIVATE_GROUP
import ru.digitalpunk.lumi.engine.TabGroup
import ru.digitalpunk.lumi.engine.TabId
import ru.digitalpunk.lumi.engine.TabInfo
import ru.digitalpunk.lumi.engine.TabsState
import ru.digitalpunk.lumi.tabs.snapshotIds
import ru.digitalpunk.lumi.ui.tabgrid.CardAction
import ru.digitalpunk.lumi.ui.tabgrid.ChipKind
import ru.digitalpunk.lumi.ui.tabgrid.cardActions
import ru.digitalpunk.lumi.ui.tabgrid.chips
import ru.digitalpunk.lumi.ui.tabgrid.currentGroupTabCount
import ru.digitalpunk.lumi.ui.tabgrid.moveRows
import ru.digitalpunk.lumi.ui.tabgrid.privateChipCount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GroupRulesPrivateTest {
    private val g = GroupId("g")
    private val a = TabInfo(TabId("a"), "", "https://a", false, false)
    private val p = TabInfo(TabId("p"), "", "https://p", false, false, PRIVATE_GROUP)
    private val state = TabsState(listOf(a, p), a.id, listOf(TabGroup(g, "G")), privateCount = 1)
    private val locked = state.copy(tabs = listOf(a), privateLocked = true, currentGroup = PRIVATE_GROUP)

    @Test fun privateChipComesFirstWithoutACount() {
        val first = chips(state).first()
        assertEquals(ChipKind.Private, first.kind)
        assertEquals(PRIVATE_GROUP, first.group)
        assertEquals(listOf(ChipKind.Private, ChipKind.Shared, ChipKind.Named), chips(state).map { it.kind })
        assertEquals(1, chips(state)[1].count)
    }

    @Test fun privateChipIsNotAMoveTarget() {
        assertTrue(moveRows(state.groups, null).none { it.group?.id == PRIVATE_GROUP })
    }

    @Test fun privateCardOffersOnlyClose() {
        assertEquals(listOf(CardAction.Close), cardActions(PRIVATE_GROUP))
    }

    @Test fun lockedPrivateChipDescriptionHasNoCount() {
        assertNull(privateChipCount(locked))
        assertEquals(1, privateChipCount(state))
    }

    @Test fun currentGroupTabCountForLockedPrivateIsTheSelectedTabsGroup() {
        assertEquals(1, currentGroupTabCount(locked))
    }

    @Test fun snapshotLiveSetExcludesPrivateTabs() {
        assertEquals(setOf(a.id), snapshotIds(state))
    }
}
