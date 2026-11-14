package ru.digitalpunk.lumi.engine

import org.json.JSONObject
import org.mozilla.geckoview.WebExtension

/**
 * The page-end content script's report (see docs: 012-page-end-spacer-extension): whether the page has a painted bar
 * stuck to the screen bottom (a translucent one too), that bar's colour, and the colour for the status
 * bar (theme-color, else a header stuck to the top). Colours are opaque ARGB; null when the page gave none the script
 * could read.
 */
internal data class PageReport(val bottomBar: Boolean = false, val bottomColor: Int? = null, val statusColor: Int? = null) {
    companion object {
        val NONE = PageReport()
    }
}

private val REPORT_KEYS = setOf("bottomBar", "bottomColor", "statusColor")

/**
 * `{bottomBar: Boolean, bottomColor?: "#RRGGBB", statusColor?: "#RRGGBB"}` from our extension's content script in the
 * top frame. Null for any other message or sender, an unknown key, a malformed colour, or a bottom colour without a bar.
 */
internal fun pageReport(message: Any?, topLevel: Boolean, envType: Int, extensionId: String?): PageReport? {
    if (!topLevel || envType != WebExtension.MessageSender.ENV_TYPE_CONTENT_SCRIPT || extensionId != PageEndSpacer.ID) return null
    val json = message as? JSONObject ?: return null
    val keys = json.keys().asSequence().toSet()
    if ("bottomBar" !in keys || !REPORT_KEYS.containsAll(keys)) return null
    val bar = json.opt("bottomBar") as? Boolean ?: return null
    val bottom = if ("bottomColor" in keys) siteColor(json.opt("bottomColor")) ?: return null else null
    val status = if ("statusColor" in keys) siteColor(json.opt("statusColor")) ?: return null else null
    if (bottom != null && !bar) return null
    return PageReport(bar, bottom, status)
}

/** "#RRGGBB" as opaque ARGB; null for anything else. Each digit is checked by hand: `toInt(16)` takes a sign. */
internal fun siteColor(value: Any?): Int? {
    val s = value as? String ?: return null
    if (s.length != 7 || s[0] != '#') return null
    var rgb = 0
    for (c in s.substring(1)) {
        rgb = rgb * 16 + when (c) {
            in '0'..'9' -> c - '0'
            in 'a'..'f' -> c - 'a' + 10
            in 'A'..'F' -> c - 'A' + 10
            else -> return null
        }
    }
    return rgb or (0xFF shl 24)
}
