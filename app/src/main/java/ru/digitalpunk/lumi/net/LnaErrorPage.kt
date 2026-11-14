package ru.digitalpunk.lumi.net

/**
 * The page for a load Gecko refused for local network access (error 195, ADR 015): no script, and a plain link back to
 * [uri] only when it is a web address.
 */
fun lnaErrorHtml(title: String, body: String, retry: String, uri: String): String {
    val link = if (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)) {
        "<a href=\"${escape(uri)}\">${escape(retry)}</a>"
    } else {
        ""
    }
    return """<!DOCTYPE html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escape(title)}</title><style>
body{font-family:sans-serif;margin:0;padding:48px 24px;background:#fdf8fd;color:#1c1b1f}
h1{font-size:22px;font-weight:500}p{font-size:16px;line-height:1.5;color:#49454f}
a{display:inline-block;box-sizing:border-box;min-height:48px;line-height:28px;margin-top:16px;padding:10px 24px;border-radius:20px;background:#6750a4;color:#fff;text-decoration:none}
@media (prefers-color-scheme: dark){body{background:#141218;color:#e6e0e9}p{color:#cac4d0}a{background:#d0bcff;color:#381e72}}
</style></head><body><h1>${escape(title)}</h1><p>${escape(body)}</p>$link</body></html>"""
}

private fun escape(text: String): String = buildString {
    text.forEach {
        when (it) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(it)
        }
    }
}
