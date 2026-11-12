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
 * tab grid shows (null is the shared list, [PRIVATE_GROUP] the private tabs). Outside the grid it is the group of the selected tab.
 * [privateCount] counts the private tabs even while [privateLocked] keeps them out of [tabs]; then [selectedId] is a
 * normal tab, and [privateResume] says a private tab was in front when they locked and comes back on unlock.
 */
data class TabsState(
    val tabs: List<TabInfo> = emptyList(),
    val selectedId: TabId? = null,
    val groups: List<TabGroup> = emptyList(),
    val currentGroup: GroupId? = null,
    val privateCount: Int = 0,
    val privateLocked: Boolean = false,
    val privateResume: Boolean = false,
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

    /** A cold start is locked; the first private tab with none hidden unlocks without a prompt. */
    private var locked = true
    private var resume: TabId? = null
    /** The private set is in view: the current group is the private one (in front, or the Private chip of the grid). */
    private var privateInView = false
    private var all: List<TabInfo> = emptyList()

    /** The last normal tab that was selected; where a locked or saved private selection falls back to. */
    var lastNormal: TabId? = null
        private set

    private val mutableTabs = MutableStateFlow(TabsState())
    val tabs: StateFlow<TabsState> = mutableTabs.asStateFlow()
    private val mutableSelected = MutableStateFlow(PageState())
    /** The page of the selected tab only. A change in another tab never touches it. */
    val selectedPage: StateFlow<PageState> = mutableSelected.asStateFlow()

    private fun pageOf(id: TabId): PageState = pages[id] ?: PageState()

    private fun isPrivate(id: TabId) = list.groupOf[id] == PRIVATE_GROUP

    /** Every tab, private ones too even while locked. For the engine's save paths only. */
    fun allTabs(): List<TabInfo> = all

    fun privateIds(): List<TabId> = list.idsIn(PRIVATE_GROUP)

    /** A private tab goes to the private set; a normal one to the current group, or the shared list from the private set. */
    fun add(id: TabId, private: Boolean = false) {
        if (private) {
            if (privateIds().isEmpty()) locked = false
        } else {
            resume = null
        }
        list = list.add(id, if (private) PRIVATE_GROUP else groups.current.takeIf { it != PRIVATE_GROUP })
        groups = groups.setCurrent(list.groupOf[list.selected])
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

    /** A private tab cannot be selected while locked; a normal one drops the pending private resume. */
    fun select(id: TabId) {
        if (id in pages && isPrivate(id)) {
            if (locked) return
        } else {
            resume = null
        }
        list = list.select(id)
        groups = groups.setCurrent(list.groupOf[list.selected])
        publish()
    }

    /** Appends stored private tabs, locked, without selecting one; a private tab opened before them is locked too. */
    fun restorePrivate(items: List<RestoredTab>) {
        val fresh = items.filter { it.id !in pages }
        fresh.forEach {
            list = list.copy(ids = list.ids + it.id, groupOf = list.groupOf + (it.id to PRIVATE_GROUP))
            pages[it.id] = PageState(url = it.url)
            titles[it.id] = it.title
        }
        if (fresh.isNotEmpty()) lock() else publish()
    }

    /** Hides the private tabs; a private tab in front gives way to the last normal tab and comes back on [unlock]. */
    fun lock() {
        hide(resumable = true)
        publish()
    }

    /** Locks; a private tab in front gives way to the last normal tab, and comes back on [unlock] only when [resumable]. Not resumable drops a pending resume. */
    private fun hide(resumable: Boolean) {
        locked = true
        if (!resumable) resume = null
        val selected = list.selected
        if (selected != null && isPrivate(selected)) {
            if (resumable) resume = selected
            val normal = lastNormal?.takeIf { it in pages && !isPrivate(it) } ?: list.ids.firstOrNull { !isPrivate(it) }
            if (normal != null) list = list.select(normal)
        }
    }

    fun unlock() {
        locked = false
        resume?.takeIf { it in pages }?.let {
            list = list.select(it)
            groups = groups.setCurrent(PRIVATE_GROUP)
        }
        resume = null
        publish()
    }

    /** Gives up the pending private resume; the current group becomes the selected tab's. */
    fun leavePrivate() {
        resume = null
        groups = groups.setCurrent(list.groupOf[list.selected])
        publish()
    }

    /** Closes every private tab; their ids. */
    fun dropPrivate(): List<TabId> {
        val doomed = privateIds()
        doomed.forEach { list = list.close(it).list }
        doomed.forEach {
            pages.remove(it)
            titles.remove(it)
        }
        resume = null
        if (groups.current == PRIVATE_GROUP) groups = groups.setCurrent(list.groupOf[list.selected])
        publish()
        return doomed
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

    /** False for an unknown tab or group, a private tab, or when the tab is already there. */
    fun moveTab(id: TabId, group: GroupId?): Boolean {
        if (id !in pages || isPrivate(id) || (group != null && !groups.contains(group))) return false
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

    /** Leaving the private set inside the app locks it, as going to the background does, but with nothing to resume. */
    private fun publish() {
        val inView = groups.current == PRIVATE_GROUP
        if (privateInView && !inView) hide(resumable = false)
        privateInView = inView
        all = list.ids.map {
            val page = pageOf(it)
            TabInfo(it, titles[it].orEmpty(), page.url, page.isLoading, page.isCrashed, list.groupOf[it])
        }
        list.selected?.takeUnless(::isPrivate)?.let { lastNormal = it }
        val count = all.count { it.isPrivate }
        val hidden = locked && count > 0
        mutableTabs.value = TabsState(
            if (hidden) all.filterNot { it.isPrivate } else all,
            list.selected,
            groups.groups,
            groups.current,
            count,
            hidden,
            resume != null,
        )
        mutableSelected.value = list.selected?.let(::pageOf) ?: PageState()
    }
}
