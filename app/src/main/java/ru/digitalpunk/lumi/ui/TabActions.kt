package ru.digitalpunk.lumi.ui

import ru.digitalpunk.lumi.engine.TabId

/** What the bars need from the tabs: the hosts of the neighbours (null at either end) and two actions. */
class TabActions(
    val selectedId: TabId?,
    val prevHost: String?,
    val nextHost: String?,
    val newTab: () -> Unit,
    /** +1 for the next tab, -1 for the previous one. */
    val switchBy: (Int) -> Unit,
)
