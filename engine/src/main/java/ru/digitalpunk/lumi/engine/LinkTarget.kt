package ru.digitalpunk.lumi.engine

/** A link the person long-pressed in the selected tab; [url] is always http or https. */
data class LinkTarget(val tab: TabId, val url: String, val title: String?, val inPrivateTab: Boolean)

/** Longer URLs get no menu: they could overflow a share intent. */
const val MAX_LINK_LENGTH = 32_768
internal const val MAX_LINK_TITLE = 200

/** The trimmed link if it is http or https (any case) with something after the scheme and at most [MAX_LINK_LENGTH] chars; else null. */
fun webLink(raw: String?): String? {
    val url = raw?.trim() ?: return null
    val scheme = listOf("http://", "https://").firstOrNull { url.startsWith(it, ignoreCase = true) } ?: return null
    return url.takeIf { it.length > scheme.length && it.length <= MAX_LINK_LENGTH }
}

/**
 * The link's title attribute, else its text; format characters (bidi overrides, zero-width) dropped except the ZWNJ that
 * Persian and Arabic words need, so a page cannot spoof an address; whitespace collapsed, cut to [MAX_LINK_TITLE] chars,
 * never inside a surrogate pair; null when both are blank.
 */
internal fun linkTitle(title: String?, text: String?): String? =
    listOf(title, text).firstNotNullOfOrNull { it?.withoutFormatChars()?.trim()?.replace(Regex("\\s+"), " ")?.ifEmpty { null } }?.take(MAX_LINK_TITLE)
        ?.let { if (it.last().isHighSurrogate()) it.dropLast(1) else it }

private fun String.withoutFormatChars() = buildString {
    this@withoutFormatChars.codePoints().forEach { if (Character.getType(it) != Character.FORMAT.toInt() || it == ZWNJ) appendCodePoint(it) }
}

private const val ZWNJ = 0x200C
