package ru.digitalpunk.lumi.engine

internal data class CloseResult(val list: TabList, val wasLast: Boolean)

/**
 * The order of the tabs and which one is selected. Immutable; every change returns a new list.
 * The order is global; a group's order is the subsequence of its tabs. A tab missing from [groupOf] is in the shared list.
 */
internal data class TabList(
    val ids: List<TabId> = emptyList(),
    val selected: TabId? = null,
    val groupOf: Map<TabId, GroupId> = emptyMap(),
) {
    fun idsIn(group: GroupId?): List<TabId> = ids.filter { groupOf[it] == group }

    /**
     * The new tab becomes the selected one. It goes right after the selected tab when that tab is in [group];
     * else after the last tab of [group]; if the group is empty, right after the selected tab.
     */
    fun add(id: TabId, group: GroupId? = groupOf[selected]): TabList {
        val last = if (groupOf[selected] == group) selected else idsIn(group).lastOrNull() ?: selected
        val at = ids.indexOf(last).let { if (it < 0) ids.size else it + 1 }
        return TabList(ids.toMutableList().apply { add(at, id) }, id, groupOf.with(id, group))
    }

    /**
     * Closing the selected tab selects the one that takes its place in its group, else the previous one there;
     * if the group is now empty, the nearest shared tab, then any normal tab. [CloseResult.wasLast] is true when no
     * normal tab is left: a private tab is never chosen for a closed normal one.
     */
    fun close(id: TabId): CloseResult {
        val at = ids.indexOf(id)
        if (at < 0) return CloseResult(this, wasLast = false)
        val rest = ids - id
        val normalRest = rest.filter { groupOf[it] != PRIVATE_GROUP }
        val inGroup = idsIn(groupOf[id])
        val groupRest = inGroup - id
        val next = when {
            id != selected -> selected
            groupRest.isNotEmpty() -> groupRest[inGroup.indexOf(id).coerceAtMost(groupRest.lastIndex)]
            else -> nearest(normalRest.toSet(), at)
        }
        return CloseResult(TabList(rest, next, groupOf - id), wasLast = normalRest.isEmpty())
    }

    /** The closest of [keep] to the place [at]: a shared tab before it, else a shared one after it, else any tab. */
    private fun nearest(keep: Set<TabId>, at: Int): TabId? {
        val before = ids.take(at).filter { it in keep }
        val after = ids.drop(at + 1).filter { it in keep }
        return before.lastOrNull { it !in groupOf } ?: after.firstOrNull { it !in groupOf } ?: before.lastOrNull() ?: after.firstOrNull()
    }

    /** Closes every tab of [group]. [CloseResult.wasLast] is true when no normal tab is left. */
    fun removeGroup(group: GroupId): CloseResult {
        val doomed = idsIn(group).toSet()
        if (doomed.isEmpty()) return CloseResult(this, wasLast = false)
        val rest = ids.filter { it !in doomed }
        val normalRest = rest.filter { groupOf[it] != PRIVATE_GROUP }
        val next = if (selected !in doomed) selected else nearest(normalRest.toSet(), ids.indexOf(selected))
        return CloseResult(TabList(rest, next, groupOf - doomed), wasLast = normalRest.isEmpty())
    }

    /** Puts the tab in [group] (null is the shared list), after that group's last tab; into an empty group it keeps its place. */
    fun move(id: TabId, group: GroupId?): TabList {
        if (id !in ids || groupOf[id] == group) return this
        val rest = ids - id
        val members = rest.filter { groupOf[it] == group }
        val anchor = members.lastOrNull()
        val order = if (anchor == null) ids else rest.toMutableList().apply { add(indexOf(anchor) + 1, id) }
        return copy(ids = order, groupOf = groupOf.with(id, group))
    }

    fun select(id: TabId): TabList = if (id in ids) copy(selected = id) else this

    /** The tab [offset] places away inside the selected tab's group. */
    fun neighbour(offset: Int): TabId? {
        if (selected == null) return null
        val inGroup = idsIn(groupOf[selected])
        return inGroup.getOrNull(inGroup.indexOf(selected) + offset)
    }

    private fun Map<TabId, GroupId>.with(id: TabId, group: GroupId?) = if (group == null) this - id else this + (id to group)
}
