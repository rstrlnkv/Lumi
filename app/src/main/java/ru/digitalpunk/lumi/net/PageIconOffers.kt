package ru.digitalpunk.lumi.net

import ru.digitalpunk.lumi.data.icons.IconCandidate
import ru.digitalpunk.lumi.data.icons.PageIconOffer
import ru.digitalpunk.lumi.data.icons.isSvg
import ru.digitalpunk.lumi.engine.PageIcons

/** The engine's icon links of an opened page as icon candidates; `:data` checks every URL again. */
fun pageIconOffer(icons: PageIcons) = PageIconOffer(
    icons.page,
    icons.links.map { link ->
        val svg = isSvg(link.url, link.type)
        IconCandidate(link.url, if (link.touch) IconCandidate.Kind.Touch else IconCandidate.Kind.Icon, if (svg) null else link.size, svg = svg)
    },
    icons.manifest,
    icons.themeColor,
)
