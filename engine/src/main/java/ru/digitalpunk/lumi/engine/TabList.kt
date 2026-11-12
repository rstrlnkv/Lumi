package ru.digitalpunk.lumi.engine

internal data class CloseResult(val list: TabList, val wasLast: Boolean)

/** The order of the tabs and which one is selected. Immutable; every change returns a new list. */
internal data class TabList(val ids: List<TabId> = emptyList(), val selected: TabId? = null) {
    /** The new tab goes right after the selected one and becomes the selected one. */
    fun add(id: TabId): TabList {
        val at = ids.indexOf(selected).let { if (it < 0) ids.size else it + 1 }
        return TabList(ids.toMutableList().apply { add(at, id) }, id)
    }

    /** Closing the selected tab selects the one that takes its place, else the previous one. */
    fun close(id: TabId): CloseResult {
        val at = ids.indexOf(id)
        if (at < 0) return CloseResult(this, wasLast = false)
        val rest = ids - id
        val next = when {
            id != selected -> selected
            rest.isEmpty() -> null
            else -> rest[at.coerceAtMost(rest.lastIndex)]
        }
        return CloseResult(TabList(rest, next), wasLast = rest.isEmpty())
    }

    fun select(id: TabId): TabList = if (id in ids) copy(selected = id) else this

    fun neighbour(offset: Int): TabId? = ids.getOrNull(ids.indexOf(selected) + offset).takeIf { selected != null }
}
