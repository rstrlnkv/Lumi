package ru.digitalpunk.lumi.address

import ru.digitalpunk.lumi.engine.siteKey

/** What the shield at the start of the address shows. */
sealed interface SiteStatus {
    /** No web page (blank, `about:`): no icon. */
    data object None : SiteStatus

    /** An https page with protection on and [count] trackers blocked so far. */
    data class Protected(val count: Int) : SiteStatus

    /** An https page whose site the user excluded from protection. */
    data object ProtectionOff : SiteStatus

    /** An http page, whatever its protection; "secure" means https until M6b reads the real certificate state. */
    data object Insecure : SiteStatus
}

/** A [private] tab always runs strict: the stored exceptions never apply to it. */
fun siteStatus(url: String, blockedTrackers: Int, offHosts: Set<String>, private: Boolean = false): SiteStatus {
    val host = siteKey(url) ?: return SiteStatus.None
    return when {
        !url.startsWith("https://", ignoreCase = true) -> SiteStatus.Insecure
        !private && host in offHosts -> SiteStatus.ProtectionOff
        else -> SiteStatus.Protected(blockedTrackers)
    }
}

/** The trackers row of the protection sheet. */
sealed interface TrackersRow {
    data object Off : TrackersRow
    data object None : TrackersRow
    data class Count(val count: Int) : TrackersRow
}

/** The real blocked [count] on https and http alike; [off] is the stored choice for the site. */
fun trackersRow(off: Boolean, count: Int): TrackersRow = when {
    off -> TrackersRow.Off
    count <= 0 -> TrackersRow.None
    else -> TrackersRow.Count(count)
}

/** The number beside the shield: nothing at 0, "99+" from 100. */
fun countLabel(count: Int): String = when {
    count <= 0 -> ""
    count >= 100 -> "99+"
    else -> count.toString()
}
