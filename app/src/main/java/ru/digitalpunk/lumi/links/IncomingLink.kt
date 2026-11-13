package ru.digitalpunk.lumi.links

import android.content.Intent
import ru.digitalpunk.lumi.address.resolveInput
import ru.digitalpunk.lumi.engine.MAX_LINK_LENGTH
import ru.digitalpunk.lumi.engine.webLink

/** What another app sent, as raw strings, so the rule runs without an [Intent]. Capped at intake. */
data class IncomingIntent(val action: String?, val data: String?, val query: String?)

/** Longer system web searches are ignored. */
const val MAX_QUERY_LENGTH = 2_048

/** The intake: data and query capped one char over each limit, so [incomingUrl] still refuses an oversize one. */
fun incomingIntent(action: String?, data: String?, query: String?) =
    IncomingIntent(action, data?.take(MAX_LINK_LENGTH + 1), query?.take(MAX_QUERY_LENGTH + 1))

/**
 * The http(s) URL to open in a new tab, or null to ignore the intent. VIEW opens its data; WEB_SEARCH opens its query when
 * it is an address, else searches it with the engine whose prefix is [searchPrefix]. Every other scheme and action is refused.
 */
fun incomingUrl(incoming: IncomingIntent, searchPrefix: String): String? = when (incoming.action) {
    Intent.ACTION_VIEW -> incoming.data.cleanUrl()
    Intent.ACTION_WEB_SEARCH -> incoming.query?.takeIf { it.length <= MAX_QUERY_LENGTH }?.let { resolveInput(it, searchPrefix) }.cleanUrl()
    else -> null
}

private fun String?.cleanUrl(): String? = webLink(this)?.takeIf { url -> url.none { it.isISOControl() } }
