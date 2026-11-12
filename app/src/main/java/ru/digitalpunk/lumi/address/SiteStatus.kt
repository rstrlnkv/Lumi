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

fun siteStatus(url: String, blockedTrackers: Int, offHosts: Set<String>): SiteStatus {
    val host = siteKey(url) ?: return SiteStatus.None
    return when {
        !url.startsWith("https://", ignoreCase = true) -> SiteStatus.Insecure
        host in offHosts -> SiteStatus.ProtectionOff
        else -> SiteStatus.Protected(blockedTrackers)
    }
}

/** The number beside the shield: nothing at 0, "99+" from 100. */
fun countLabel(count: Int): String = when {
    count <= 0 -> ""
    count >= 100 -> "99+"
    else -> count.toString()
}
