package ru.digitalpunk.lumi.data.icons

/** An SVG icon larger than this is not read. */
internal const val MAX_SVG_BYTES = 256 * 1024
private const val MAX_TAGS = 4000
private const val MAX_DEPTH = 64
/** Each `<use>` may draw a group holding more `<use>`s, so a few dozen of them can mean billions of draws. */
private const val MAX_USES = 4
private val USE_TAG = Regex("<use[\\s/>]")
private val BANNED = listOf(
    "<script", "<foreignobject", "<iframe", "<image", "<feimage", "<!entity", "<?xml-stylesheet", "@import",
    "<filter", "<pattern", "<mask", "stroke-dasharray",
)
private val EVENT_ATTR = Regex("[\\s/]on[a-z]+\\s*=")
private val SVG_START = Regex("^(?:<\\?xml[^>]{0,200}>\\s*)?(?:<!--[^>]{0,500}-->\\s*)*(?:<!doctype[^>\\[]{0,300}>\\s*)?<svg[\\s>/]")

/** Starts as an SVG document: `<svg`, after a BOM, whitespace, an XML declaration, comments and a doctype. */
fun looksLikeSvg(bytes: ByteArray): Boolean {
    val head = String(bytes, 0, minOf(bytes.size, 1024), Charsets.UTF_8).trimStart('\uFEFF').trimStart().lowercase()
    return SVG_START.containsMatchIn(head)
}

/**
 * True when an SVG icon can be drawn without reaching outside or costing much: no script, foreign content, external image,
 * entity, internal doctype subset, style sheet import, filter, pattern, mask or dash; every `href` and `url(` points inside
 * the file (`#`); no event attribute; at most 4 `<use>` tags and 4000 tags in all, nesting at most 64, every tag closed.
 * Linear in the text.
 */
fun svgSafe(text: String): Boolean {
    val s = text.lowercase()
    if (BANNED.any { it in s } || ("<!doctype" in s && '[' in s.substringAfter("<!doctype").substringBefore('>'))) return false
    if (!onlyLocal(s, "href") || !onlyLocal(s, "url(") || EVENT_ATTR.containsMatchIn(s)) return false
    if (USE_TAG.findAll(s).take(MAX_USES + 1).count() > MAX_USES) return false
    var tags = 0; var depth = 0; var inTag = false; var closing = false; var special = false
    for (i in s.indices) {
        val c = s[i]
        if (!inTag) {
            if (c != '<') continue
            inTag = true
            val next = s.getOrNull(i + 1)
            closing = next == '/'
            special = next == '?' || next == '!'
            if (!closing && !special && ++tags > MAX_TAGS) return false
        } else if (c == '<') {
            return false
        } else if (c == '>') {
            inTag = false
            if (closing) depth-- else if (!special && s[i - 1] != '/' && ++depth > MAX_DEPTH) return false
        }
    }
    return !inTag
}

/** Every [token] (`href` or `url(`) is followed, after `=`, quotes and spaces, by `#`. */
private fun onlyLocal(s: String, token: String): Boolean {
    var at = s.indexOf(token)
    while (at >= 0) {
        var i = at + token.length
        while (i < s.length && (s[i].isWhitespace() || s[i] == '=' || s[i] == '"' || s[i] == '\'')) i++
        if (i < s.length && s[i] != '#' && (token == "url(" || s[at + token.length].let { it == '=' || it.isWhitespace() })) return false
        at = s.indexOf(token, at + token.length)
    }
    return true
}

/** At least 2 % of the pixels show something: an SVG the renderer could not draw comes out blank. */
fun hasInk(pixels: IntArray): Boolean = pixels.count { it ushr 24 != 0 } * 50 >= pixels.size
