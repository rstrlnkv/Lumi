package ru.digitalpunk.lumi.data.tabs

/** An in-memory [TabDao] for the repository tests. */
internal class FakeTabDao : TabDao {
    var rows = emptyList<TabEntity>()
    var selected: String? = null
    var groupRows = emptyList<TabGroupEntity>()
    var currentGroup: String? = null
    override suspend fun tabs() = rows
    override suspend fun selectedTabId() = selected
    override suspend fun groups() = groupRows
    override suspend fun currentGroupId() = currentGroup
    override suspend fun deleteGroups() { groupRows = emptyList() }
    override suspend fun insertGroups(groups: List<TabGroupEntity>) { groupRows = groups }
    override suspend fun deleteTabs() { rows = emptyList() }
    override suspend fun insertTabs(tabs: List<TabEntity>) { rows = tabs }
    override suspend fun insertSelection(selection: TabSelectionEntity) {
        selected = selection.tabId
        currentGroup = selection.currentGroupId
    }
}
