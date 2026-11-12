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
)

/** An empty list means the tabs are not restored yet. */
data class TabsState(val tabs: List<TabInfo> = emptyList(), val selectedId: TabId? = null)

internal class RestoredTab(val id: TabId, val url: String, val title: String)

/** The tab list with each tab's page state and title. Pure, so it runs on the JVM. */
internal class TabsStore {
    private var list = TabList()
    private val pages = HashMap<TabId, PageState>()
    private val titles = HashMap<TabId, String>()

    private val mutableTabs = MutableStateFlow(TabsState())
    val tabs: StateFlow<TabsState> = mutableTabs.asStateFlow()
    private val mutableSelected = MutableStateFlow(PageState())
    /** The page of the selected tab only. A change in another tab never touches it. */
    val selectedPage: StateFlow<PageState> = mutableSelected.asStateFlow()

    private fun pageOf(id: TabId): PageState = pages[id] ?: PageState()

    fun add(id: TabId) {
        list = list.add(id)
        pages[id] = PageState()
        titles[id] = ""
        publish()
    }

    fun restore(items: List<RestoredTab>, selected: TabId?) {
        list = TabList(items.map { it.id }, selected?.takeIf { s -> items.any { it.id == s } } ?: items.firstOrNull()?.id)
        items.forEach {
            pages[it.id] = PageState(url = it.url)
            titles[it.id] = it.title
        }
        publish()
    }

    fun select(id: TabId) {
        list = list.select(id)
        publish()
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
                TabInfo(it, titles[it].orEmpty(), page.url, page.isLoading, page.isCrashed)
            },
            list.selected,
        )
        mutableSelected.value = list.selected?.let(::pageOf) ?: PageState()
    }
}
