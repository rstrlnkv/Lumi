package ru.digitalpunk.lumi.data.search

private const val MAX_ITEMS = 20
private const val MAX_ITEM_LENGTH = 200

/**
 * The suggestions of an OpenSearch answer, `["query",["s1","s2",...], ...]`: one forward pass, no regex, no recursion.
 * Everything after the list is ignored. Null for any other shape, a non-string item, a bad escape or a lone surrogate.
 * At most [MAX_ITEMS]; control characters become spaces, format characters (bidi overrides, zero-width joiners) are
 * dropped, items are trimmed, and blank or over-long ones are dropped.
 */
internal fun parseSuggestions(json: String): List<String>? {
    val p = JsonCursor(json)
    if (!p.take('[') || p.string() == null || !p.take(',') || !p.take('[')) return null
    val out = ArrayList<String>()
    if (p.take(']')) return out
    while (true) {
        val item = p.string() ?: return null
        val clean = item.filter { Character.getType(it) != Character.FORMAT.toInt() }
            .map { if (it.isISOControl()) ' ' else it }.joinToString("").trim()
        if (clean.isNotEmpty() && clean.length <= MAX_ITEM_LENGTH && out.size < MAX_ITEMS) out += clean
        if (p.take(']')) return out
        if (!p.take(',')) return null
    }
}

private class JsonCursor(private val s: String) {
    private var i = 0

    /** Skips whitespace, then consumes [c] if it is next. */
    fun take(c: Char): Boolean {
        while (i < s.length && s[i].isWhitespace()) i++
        if (i < s.length && s[i] == c) {
            i++
            return true
        }
        return false
    }

    /** A JSON string, or null when the next value is not one or is malformed. */
    fun string(): String? {
        if (!take('"')) return null
        val out = StringBuilder()
        while (i < s.length) {
            val c = s[i++]
            when (c) {
                '"' -> return out.toString().takeIf(::surrogatesPaired)
                '\\' -> {
                    if (i >= s.length) return null
                    when (val e = s[i++]) {
                        '"', '\\', '/' -> out.append(e)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (i + 4 > s.length) return null
                            val hex = s.substring(i, i + 4)
                            if (!hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) return null
                            out.append(hex.toInt(16).toChar())
                            i += 4
                        }
                        else -> return null
                    }
                }
                else -> out.append(c)
            }
        }
        return null
    }

    private fun surrogatesPaired(t: String): Boolean {
        var k = 0
        while (k < t.length) {
            val c = t[k]
            if (c.isHighSurrogate()) {
                if (k + 1 >= t.length || !t[k + 1].isLowSurrogate()) return false
                k += 2
            } else {
                if (c.isLowSurrogate()) return false
                k++
            }
        }
        return true
    }
}
