package ru.digitalpunk.lumi.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TabInfo(
    val id: TabId,
    val title: String,
    val url: String,
    val isLoading: Boolean,
    val isCrashed: Boolean,
    /** Null is the shared list. */
    val groupId: GroupId? = null,
)

/**
 * An empty list means the tabs are not restored yet. [groups] are in creation order; [currentGroup] is the group the
 * tab grid shows (null is the shared list). Outside the grid it is the group of the selected tab.
 */
data class TabsState(
    val tabs: List<TabInfo> = emptyList(),
    val selectedId: TabId? = null,
    val groups: List<TabGroup> = emptyList(),
    val currentGroup: GroupId? = null,
)

/** The tabs of [group] (null is the shared list), in order. */
fun TabsState.tabsIn(group: GroupId?): List<TabInfo> = tabs.filter { it.groupId == group }

/** The tab [offset] places away inside the selected tab's group; null at either end. */
fun TabsState.neighbour(offset: Int): TabInfo? {
    val selected = tabs.firstOrNull { it.id == selectedId } ?: return null
    val inGroup = tabsIn(selected.groupId)
    return inGroup.getOrNull(inGroup.indexOf(selected) + offset)
}

internal class RestoredTab(val id: TabId, val url: String, val title: String, val groupId: GroupId? = null)

/** The tab list with each tab's page state and title. Pure, so it runs on the JVM. */
internal class TabsStore {
    private var list = TabList()
    private var groups = TabGroups()
    private val pages = HashMap<TabId, PageState>()
    private val titles = HashMap<TabId, String>()

    private val mutableTabs = MutableStateFlow(TabsState())
    val tabs: StateFlow<TabsState> = mutableTabs.asStateFlow()
    private val mutableSelected = MutableStateFlow(PageState())
    /** The page of the selected tab only. A change in another tab never touches it. */
    val selectedPage: StateFlow<PageState> = mutableSelected.asStateFlow()

    private fun pageOf(id: TabId): PageState = pages[id] ?: PageState()

    fun add(id: TabId) {
        list = list.add(id, groups.current)
        pages[id] = PageState()
        titles[id] = ""
        publish()
    }

    /** A tab whose group is gone goes to the shared list and keeps its place; a current group that is gone becomes the selected tab's group. */
    fun restore(items: List<RestoredTab>, selected: TabId?, storedGroups: List<TabGroup> = emptyList(), currentGroup: GroupId? = null) {
        val kept = TabGroups.restore(storedGroups)
        val known = kept.mapTo(HashSet()) { it.id }
        val groupOf = items.mapNotNull { t -> t.groupId?.takeIf { it in known }?.let { t.id to it } }.toMap()
        list = TabList(
            items.map { it.id },
            selected?.takeIf { s -> items.any { it.id == s } } ?: items.firstOrNull()?.id,
            groupOf,
        )
        groups = TabGroups(kept, if (currentGroup == null || currentGroup in known) currentGroup else list.groupOf[list.selected])
        items.forEach {
            pages[it.id] = PageState(url = it.url)
            titles[it.id] = it.title
        }
        publish()
    }

    fun select(id: TabId) {
        list = list.select(id)
        groups = groups.setCurrent(list.groupOf[list.selected])
        publish()
    }

    /** Makes the group of the selected tab the current one. */
    fun showSelectedGroup() {
        groups = groups.setCurrent(list.groupOf[list.selected])
        publish()
    }

    fun setCurrentGroup(group: GroupId?) {
        groups = groups.setCurrent(group)
        publish()
    }

    /** The new group's id; null when the name is empty or taken. The current group does not change. */
    fun createGroup(name: String): GroupId? {
        val id = GroupId.random()
        groups = groups.create(name, id) ?: return null
        publish()
        return id
    }

    fun renameGroup(id: GroupId, name: String): Boolean {
        groups = groups.rename(id, name) ?: return false
        publish()
        return true
    }

    fun idsIn(group: GroupId?): List<TabId> = list.idsIn(group)

    /** Removes the group and its tabs. True when no tab is left. */
    fun deleteGroup(id: GroupId): Boolean {
        val doomed = list.idsIn(id)
        val result = list.removeGroup(id)
        list = result.list
        doomed.forEach {
            pages.remove(it)
            titles.remove(it)
        }
        groups = groups.remove(id)
        publish()
        return result.wasLast
    }

    /** False for an unknown tab or group, or when the tab is already there. */
    fun moveTab(id: TabId, group: GroupId?): Boolean {
        if (id !in pages || (group != null && !groups.contains(group))) return false
        val moved = list.move(id, group)
        if (moved == list) return false
        list = moved
        publish()
        return true
    }

    fun close(id: TabId): Boolean {
        val result = list.close(id)
        list = result.list
        pages.remove(id)
        titles.remove(id)
        publish()
        return result.wasLast
    }

    fun neighbour(offset: Int): TabId? = list.neighbour(offset)

    fun pageChanged(id: TabId, page: PageState) {
        if (id !in pages) return
        pages[id] = page
        publish()
    }

    fun titleChanged(id: TabId, title: String) {
        if (id !in titles) return
        titles[id] = title
        publish()
    }

    private fun publish() {
        mutableTabs.value = TabsState(
            list.ids.map {
                val page = pageOf(it)
                TabInfo(it, titles[it].orEmpty(), page.url, page.isLoading, page.isCrashed, list.groupOf[it])
            },
            list.selected,
            groups.groups,
            groups.current,
        )
        mutableSelected.value = list.selected?.let(::pageOf) ?: PageState()
    }
}
