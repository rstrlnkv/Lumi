package ru.digitalpunk.lumi.engine

/** A tab as the engine hands it out for storage and takes it back. [sessionState] is Gecko's session JSON. */
data class SavedTab(val id: TabId, val url: String, val title: String, val sessionState: String?, val groupId: GroupId? = null)

/** [stateVersions] changes whenever a tab's [SavedTab.sessionState] does, so the caller can skip unchanged ones. */
data class TabsSave(val tabs: List<SavedTab>, val selected: TabId?, val stateVersions: Map<TabId, Long>,
    val groups: List<TabGroup> = emptyList(),
    val currentGroup: GroupId? = null,
)

/** A stored tab whose URL the engine would refuse to load comes back blank, and its state (which could hold that URL) is dropped. */
internal fun SavedTab.sanitized(): SavedTab = if (isLoadableUrl(url)) this else copy(url = "about:blank", sessionState = null)

/** Parses stored state; null (so the caller loads the URL instead) when it is missing, fails to parse, or is empty. */
internal fun <T : Any> parseState(raw: String?, parse: (String) -> T?, isEmpty: (T) -> Boolean): T? {
    if (raw.isNullOrEmpty()) return null
    val parsed = try {
        parse(raw)
    } catch (_: Exception) {
        null
    }
    return parsed?.takeUnless(isEmpty)
}
