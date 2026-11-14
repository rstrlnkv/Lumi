package ru.digitalpunk.lumi.engine

import org.json.JSONArray
import org.json.JSONObject
import org.mozilla.geckoview.WebExtension

/** One icon link of the opened page: an https URL, a touch icon or not, its largest declared edge and its type. */
data class PageIconLink(val url: String, val touch: Boolean, val size: Int?, val type: String?)

/** The icon links of the page shown in a normal tab (see docs: 014-site-icons-and-suggestions-through-gecko). [page] is its origin. */
data class PageIcons(val page: String, val links: List<PageIconLink>, val manifest: String?, val themeColor: Int?)

private val ICON_KEYS = setOf("page", "icons", "manifest", "themeColor")
private val LINK_KEYS = setOf("u", "k", "s", "t")
private const val MAX_LINKS = 16
private const val MAX_URL = 2048
private const val MAX_EDGE = 4096
private val TYPE = Regex("[a-z0-9.+/-]{1,40}")
private val PORT = Regex("^https://[^/?#]*:([0-9]+)", RegexOption.IGNORE_CASE)

/**
 * `{page: "https://origin", icons: [{u, k: "touch"|"icon", s?, t?}], manifest?, themeColor?: "#RRGGBB"}` from our
 * extension's content script in the top frame of a normal tab. Null for a [isPrivate] tab, any other sender, and any key,
 * URL, size, type or colour out of the rules: one bad part rejects the whole message.
 */
internal fun pageIcons(message: Any?, topLevel: Boolean, envType: Int, extensionId: String?, isPrivate: Boolean): PageIcons? {
    if (isPrivate || !topLevel || envType != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT || extensionId != PageEndSpacer.ID) return null
    val json = message as? JSONObject ?: return null
    val keys = json.keys().asSequence().toSet()
    if ("page" !in keys || "icons" !in keys || !ICON_KEYS.containsAll(keys)) return null
    val page = json.opt("page") as? String ?: return null
    if (page.length > MAX_URL || !page.startsWith("https://") || fetchableHost(page) == null) return null
    val array = json.opt("icons") as? JSONArray ?: return null
    if (array.length() > MAX_LINKS) return null
    val links = (0 until array.length()).map { i -> link(array.opt(i)) ?: return null }
    val manifest = if ("manifest" in keys) iconUrl(json.opt("manifest")) ?: return null else null
    val theme = if ("themeColor" in keys) siteColor(json.opt("themeColor")) ?: return null else null
    return PageIcons(page, links, manifest, theme)
}

private fun link(value: Any?): PageIconLink? {
    val json = value as? JSONObject ?: return null
    val keys = json.keys().asSequence().toSet()
    if ("u" !in keys || "k" !in keys || !LINK_KEYS.containsAll(keys)) return null
    val url = iconUrl(json.opt("u")) ?: return null
    val touch = when (json.opt("k")) { "touch" -> true; "icon" -> false; else -> return null }
    val size = if ("s" in keys) (json.opt("s") as? Int)?.takeIf { it in 1..MAX_EDGE } ?: return null else null
    val type = if ("t" in keys) (json.opt("t") as? String)?.takeIf(TYPE::matches) ?: return null else null
    return PageIconLink(url, touch, size, type)
}

/** An https URL of at most 2048 characters that [fetchableHost] accepts, on port 443 or none. */
private fun iconUrl(value: Any?): String? {
    val url = value as? String ?: return null
    if (url.length > MAX_URL || fetchableHost(url) == null) return null
    return url.takeIf { PORT.find(it)?.groupValues?.get(1)?.let { p -> p == "443" } ?: true }
}
