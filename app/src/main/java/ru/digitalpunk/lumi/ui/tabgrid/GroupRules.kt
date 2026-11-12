package ru.digitalpunk.lumi.ui.tabgrid

import ru.digitalpunk.lumi.engine.GroupId
import ru.digitalpunk.lumi.engine.TabGroup
import ru.digitalpunk.lumi.engine.TabsState
import ru.digitalpunk.lumi.engine.groupNameTaken
import ru.digitalpunk.lumi.engine.normalizeGroupName
import ru.digitalpunk.lumi.engine.tabsIn

enum class NameProblem { Empty, Duplicate }

/** What is wrong with a typed name. [otherNames] leaves out the group's own name on rename; [reserved] is the shared list's name. */
fun groupNameProblem(raw: String, otherNames: List<String>, reserved: String): NameProblem? {
    val name = normalizeGroupName(raw)
    return when {
        name.isEmpty() -> NameProblem.Empty
        groupNameTaken(name, otherNames + reserved) -> NameProblem.Duplicate
        else -> null
    }
}

/** A row of the "Move to group" list; the tab's own group is shown but cannot be picked. */
data class MoveRow(val group: TabGroup, val isCurrent: Boolean)

fun moveRows(groups: List<TabGroup>, tabGroup: GroupId?): List<MoveRow> = groups.map { MoveRow(it, it.id == tabGroup) }

fun showsRemoveFromGroup(tabGroup: GroupId?): Boolean = tabGroup != null

/** A group with no tabs is deleted at once. */
fun deleteNeedsConfirmation(count: Int): Boolean = count > 0

/** The count the toolbar, the Tabs button and the More menu show: the tabs of the current group, not of all groups. */
fun currentGroupTabCount(state: TabsState): Int = state.tabsIn(state.currentGroup).size

/** A chip of the grid. [name] is null for the shared list, whose name is a string resource. */
data class ChipModel(val group: GroupId?, val name: String?, val count: Int)

fun chips(state: TabsState): List<ChipModel> =
    listOf(ChipModel(null, null, state.tabsIn(null).size)) + state.groups.map { ChipModel(it.id, it.name, state.tabsIn(it.id).size) }

/** Whether a chip at [offset] with [size] is cut by, or outside, the viewport; a chip fully inside is left where it is. */
fun chipNeedsScroll(offset: Int, size: Int, viewportStart: Int, viewportEnd: Int): Boolean =
    offset < viewportStart || offset + size > viewportEnd
