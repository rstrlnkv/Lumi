package ru.digitalpunk.lumi.engine

private const val MAX_BLOCKED_URIS = 1000

/** The distinct blocked tracker URIs of one page load. Gecko can report the same URI several times; it counts once. */
internal class BlockedCounter {
    private val uris = HashSet<String>()

    val count: Int get() = uris.size

    /** True if [uri] is new on this page and was counted. Stops counting at 1000 URIs. */
    fun add(uri: String): Boolean = uris.size < MAX_BLOCKED_URIS && uris.add(uri)

    fun reset() = uris.clear()
}

/**
 * Whether a content-blocking event is a blocked tracker: it was blocked and carries an anti-tracking or cookie category.
 * A safe-browsing hit alone (phishing, malware) carries neither, so it is not a tracker.
 */
internal fun countsAsTracker(antiTracking: Int, cookie: Int, blocking: Boolean): Boolean =
    blocking && (antiTracking != 0 || cookie != 0)
