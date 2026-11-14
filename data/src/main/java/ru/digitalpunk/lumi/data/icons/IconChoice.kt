package ru.digitalpunk.lumi.data.icons

import ru.digitalpunk.lumi.data.icons.IconCandidate.Kind

private const val MAX_ATTEMPTS = 4
/** An icon at least this large is sharp enough: Safari's 180 px touch icon meets it. */
private const val MEETS_PX = 180

/** The size an icon is taken to have: its `sizes`, else 180 for a touch icon (the usual file); another unknown size comes last. */
private fun assumedSize(c: IconCandidate): Int = c.size ?: if (c.kind == Kind.Touch) MEETS_PX else 0

private fun meets(c: IconCandidate) = assumedSize(c) >= MEETS_PX

/** True when no link is large enough, so the web app manifest is worth a fetch. */
fun needsManifest(links: List<IconCandidate>): Boolean = links.none(::meets)

/**
 * The order to try icons in, at most four: those that meet 180 px first (touch, manifest, icon; the smallest that meets),
 * then the rest largest first, then `/favicon.ico`. Document order breaks ties; a URL is tried once.
 */
fun rankIcons(candidates: List<IconCandidate>, rootUrl: String): List<IconCandidate> {
    val ordered = candidates.distinctBy { it.url }.sortedWith(
        compareBy<IconCandidate> { !meets(it) }
            .thenBy { if (meets(it)) assumedSize(it) else -assumedSize(it) }
            .thenBy { it.kind.ordinal },
    )
    val favicon = rootUrl + "favicon.ico"
    val first = ordered.take(MAX_ATTEMPTS)
    return if (first.any { it.url == favicon }) first else ordered.take(MAX_ATTEMPTS - 1) + IconCandidate(favicon, Kind.Favicon, null)
}
