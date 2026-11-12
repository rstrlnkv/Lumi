package ru.digitalpunk.lumi.ui

import ru.digitalpunk.lumi.engine.TabId

/** What the bars need from the tabs: how many there are, the hosts of the neighbours (null at either end) and four actions. */
class TabActions(
    val selectedId: TabId?,
    val count: Int,
    val prevHost: String?,
    val nextHost: String?,
    val newTab: () -> Unit,
    val newPrivateTab: () -> Unit,
    val openGrid: () -> Unit,
    /** +1 for the next tab, -1 for the previous one. */
    val switchBy: (Int) -> Unit,
)
