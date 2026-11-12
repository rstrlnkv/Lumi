package ru.digitalpunk.lumi.engine

/** Which tabs hold a live Gecko session, least recently selected first. */
internal class LiveSessions {
    private val order = LinkedHashSet<TabId>()

    fun touch(id: TabId) {
        order.remove(id)
        order.add(id)
    }

    fun remove(id: TabId) {
        order.remove(id)
    }

    /** The oldest tabs to drop so that at most [cap] stay. [keep] is never named. */
    fun evictions(cap: Int, keep: TabId): List<TabId> {
        val excess = order.size - cap
        return if (excess <= 0) emptyList() else order.filter { it != keep }.take(excess)
    }
}
