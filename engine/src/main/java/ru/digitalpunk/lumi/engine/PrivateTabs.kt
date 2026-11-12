package ru.digitalpunk.lumi.engine

/**
 * The private tabs' group. Never a UUID, never a named group and never stored as a group: a tab in it runs in a
 * private-mode Gecko session and stays out of the plain storage path ([plainSave]).
 */
val PRIVATE_GROUP = GroupId("private")

val TabInfo.isPrivate: Boolean get() = groupId == PRIVATE_GROUP

/** The private tabs for the encrypted store, in display order. Their [SavedTab.groupId] is [PRIVATE_GROUP]. */
data class PrivateSave(val tabs: List<SavedTab>)

internal data class PlainSave(val tabs: List<TabInfo>, val selected: TabId?, val currentGroup: GroupId?)

/**
 * The only filter on the way to plain storage: normal tabs only; a private selection falls back to [lastNormal],
 * else the first normal tab; the current group is never [PRIVATE_GROUP].
 */
internal fun plainSave(state: TabsState, lastNormal: TabId?): PlainSave {
    val tabs = state.tabs.filterNot { it.isPrivate }
    fun known(id: TabId?) = id?.takeIf { i -> tabs.any { it.id == i } }
    val selected = known(state.selectedId) ?: known(lastNormal) ?: tabs.firstOrNull()?.id
    val current = state.currentGroup.takeIf { it != PRIVATE_GROUP } ?: tabs.firstOrNull { it.id == selected }?.groupId
    return PlainSave(tabs, selected, current)
}

internal fun privateTabsOf(tabs: List<TabInfo>): List<TabInfo> = tabs.filter { it.isPrivate }

/** Stored private tabs that may be restored: checked like normal tabs, without ids already in use or repeated. */
internal fun privateRestoreItems(saved: List<SavedTab>, existing: Set<TabId>): List<SavedTab> =
    saved.filter { it.id !in existing }.distinctBy { it.id }.map { it.sanitized() }
