package ru.digitalpunk.lumi.data.icons

import ru.digitalpunk.lumi.data.icons.IconCandidate.Kind

private const val MAX_RASTERS = 3

/** The size an icon is taken to have: its `sizes`, else 180 for a touch icon (the usual file); another unknown size comes last. */
private fun assumedSize(c: IconCandidate): Int = c.size ?: if (c.kind == Kind.Touch) SHARP_PX else 0

private fun meets(c: IconCandidate) = !c.svg && assumedSize(c) >= SHARP_PX

/** True when no link is large enough, so the web app manifest is worth a fetch. */
fun needsManifest(links: List<IconCandidate>): Boolean = links.none(::meets)

/**
 * The order to try icons in, at most seven, as Safari looks for them: rasters that meet 180 px (the smallest that meets; `any`
 * before maskable), then the root `/apple-touch-icon.png` and `-precomposed.png` when the page names no touch icon, then the
 * other rasters largest first (three rasters in all), then one SVG, then `/favicon.ico`. A URL is tried once.
 */
fun rankIcons(candidates: List<IconCandidate>, rootUrl: String): List<IconCandidate> {
    val unique = candidates.distinctBy { it.url }
    val rasters = unique.filter { !it.svg }.sortedWith(
        compareBy<IconCandidate> { !meets(it) }
            .thenBy { meets(it) && it.maskable }
            .thenBy { if (meets(it)) assumedSize(it) else -assumedSize(it) }
            .thenBy { it.maskable }
            .thenBy { it.kind.ordinal },
    ).take(MAX_RASTERS)
    val probes = if (unique.any { it.kind == Kind.Touch }) emptyList()
    else listOf("apple-touch-icon.png", "apple-touch-icon-precomposed.png").map { IconCandidate(rootUrl + it, Kind.RootTouch, null) }
    val svg = unique.filter { it.svg }.sortedBy { it.maskable }.take(1)
    val favicon = IconCandidate(rootUrl + "favicon.ico", Kind.Favicon, null)
    return (rasters.filter(::meets) + probes + rasters.filterNot(::meets) + svg + favicon).distinctBy { it.url }
}
