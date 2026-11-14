package ru.digitalpunk.lumi.data.icons

import org.json.JSONException
import org.json.JSONObject
import java.net.URI

private const val MAX_ENTRIES = 30
private const val MAX_DEPTH = 32

/**
 * The usable icons of a web app manifest, resolved against [manifestUrl]: https only; `purpose` with `any` (absent counts as
 * `any`) or `maskable`; a raster needs a known size, an SVG may have none. Empty for anything that is not a manifest object.
 */
fun parseManifestIcons(json: String, manifestUrl: String): List<IconCandidate> {
    if (nestingDepth(json) > MAX_DEPTH) return emptyList()
    val icons = try { JSONObject(json).optJSONArray("icons") } catch (_: JSONException) { null } ?: return emptyList()
    return (0 until minOf(icons.length(), MAX_ENTRIES)).mapNotNull { i ->
        val icon = icons.optJSONObject(i) ?: return@mapNotNull null
        val src = icon.opt("src") as? String ?: return@mapNotNull null
        val purposes = (icon.opt("purpose") as? String ?: "any").lowercase().split(Regex("\\s+"))
        if ("any" !in purposes && "maskable" !in purposes) return@mapNotNull null
        if (src.isBlank() || src.length > MAX_URL || src.startsWith("data:", ignoreCase = true)) return@mapNotNull null
        val url = try { URI(manifestUrl).resolve(src.trim()).toString() } catch (_: Exception) { return@mapNotNull null }
        if (!isAllowedIconUrl(url)) return@mapNotNull null
        val svg = isSvg(url, icon.opt("type") as? String)
        val size = (icon.opt("sizes") as? String)?.let(::largestEdge)
        if (size == null && !svg) return@mapNotNull null
        IconCandidate(url, IconCandidate.Kind.Manifest, if (svg) null else size, svg = svg, maskable = "any" !in purposes)
    }
}

/** The deepest nesting of `[` and `{` outside strings, in one pass: org.json recurses and a hostile body would overflow the stack. */
private fun nestingDepth(json: String): Int {
    var depth = 0; var max = 0; var inString = false; var escaped = false
    for (ch in json) {
        when {
            escaped -> escaped = false
            inString -> if (ch == '\\') escaped = true else if (ch == '"') inString = false
            ch == '"' -> inString = true
            ch == '[' || ch == '{' -> { depth++; if (depth > max) max = depth }
            ch == ']' || ch == '}' -> depth--
        }
    }
    return max
}
