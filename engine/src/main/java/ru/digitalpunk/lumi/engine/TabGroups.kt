package ru.digitalpunk.lumi.engine

/** The named groups in creation order and the group the tab grid shows (null is the shared list). Immutable. */
internal data class TabGroups(val groups: List<TabGroup> = emptyList(), val current: GroupId? = null) {
    private fun has(id: GroupId?) = id == null || groups.any { it.id == id }

    fun contains(id: GroupId) = groups.any { it.id == id }

    /** Appends the group; null when the normalized name is empty or taken. */
    fun create(name: String, id: GroupId): TabGroups? {
        val clean = normalizeGroupName(name)
        if (clean.isEmpty() || groupNameTaken(clean, groups.map { it.name })) return null
        return copy(groups = groups + TabGroup(id, clean))
    }

    /** Null for an unknown id, an empty name, or a name another group has. A change of case only is fine. */
    fun rename(id: GroupId, name: String): TabGroups? {
        val clean = normalizeGroupName(name)
        if (!contains(id) || clean.isEmpty() || groupNameTaken(clean, groups.filter { it.id != id }.map { it.name })) return null
        return copy(groups = groups.map { if (it.id == id) it.copy(name = clean) else it })
    }

    /** The current group falls back to the shared list when it is the one removed. */
    fun remove(id: GroupId): TabGroups = TabGroups(groups.filter { it.id != id }, current.takeIf { it != id })

    /** An unknown group is ignored. */
    fun setCurrent(id: GroupId?): TabGroups = if (has(id)) copy(current = id) else this

    companion object {
        /** Drops groups with a blank name or a repeated id. */
        fun restore(stored: List<TabGroup>): List<TabGroup> {
            val seen = HashSet<GroupId>()
            return stored.filter { it.name.isNotBlank() && seen.add(it.id) }
        }
    }
}
