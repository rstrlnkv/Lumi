package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoSession.ContentDelegate.ContextElement

/** An image the person long-pressed; [url] is always http or https, [site] is the page's site key. */
data class ImageTarget(val url: String, val title: String?, val site: String?, val inPrivateTab: Boolean)

/** What a long press in the selected tab hit: a link, an image, or both (an image inside a link). At least one is set. */
data class ContextTarget(val tab: TabId, val inPrivateTab: Boolean, val link: LinkTarget?, val image: ImageTarget?)

/**
 * The target of a long press, or null when there is nothing to act on. Only an element of [type] image (not video or audio)
 * with an http or https source counts as an image:
 * a `data:` or `blob:` image is useless (or huge) outside the page. The image title is the alt text, else the title
 * attribute, else the file name from the URL.
 */
internal fun contextTarget(
    tab: TabId, private: Boolean, pageUrl: String, linkUri: String?, title: String?, linkText: String?,
    type: Int, srcUri: String?, altText: String?,
): ContextTarget? {
    val link = webLink(linkUri)?.let { LinkTarget(tab, it, linkTitle(title, linkText), private) }
    val image = webLink(srcUri.takeIf { type == ContextElement.TYPE_IMAGE })?.let { url ->
        ImageTarget(url, linkTitle(altText, title) ?: linkTitle(nameFromUrl(url), null), siteKey(pageUrl), private)
    }
    return if (link == null && image == null) null else ContextTarget(tab, private, link, image)
}
