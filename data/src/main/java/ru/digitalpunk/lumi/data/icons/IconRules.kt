package ru.digitalpunk.lumi.data.icons

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.IDN
import java.net.URI

/** One body read: at most this many bytes, after decompression. */
internal const val MAX_BYTES = 1_048_576
internal const val HTTP_TIMEOUT_MS = 10_000
internal const val TOTAL_TIMEOUT_MS = 30_000L
internal const val MAX_REDIRECTS = 5
/** The stored icon is this many pixels square. */
internal const val ICON_SIZE = 96
private const val MAX_ATTEMPTS = 4
private const val MAX_URL = 2048
private const val MAX_LINK_TAGS = 50
private const val MIN_EDGE = 16
private const val SMALL_BOX = 88
private const val FRESH_MS = 30L * 24 * 3_600_000
private const val CLOCK_SKEW_MS = 24L * 3_600_000

/** Where a page's icon is looked up: its host, and the https root of that host. */
data class IconTarget(val host: String, val rootUrl: String)

private val WEB_URL = Regex("^(https?)://([^/?#]*)", RegexOption.IGNORE_CASE)
private val HOST_CHARS = Regex("[a-z0-9._-]+")

/**
 * The icon host of a web page: lower case, no user info, port or trailing dot; `www.` is kept.
 * Null for anything but http(s), and for hosts a blind GET must not reach: IP literals, `localhost`,
 * single-label names and `.local`/`.localhost`/`.internal`. Any page maps to the https root on port 443.
 */
fun iconTarget(url: String): IconTarget? {
    val match = WEB_URL.find(url.trim()) ?: return null
    // The URL spec and Gecko read `\` as `/`, so the user-info split below would pick the wrong host.
    if ('\\' in match.groupValues[2]) return null
    val hostPort = match.groupValues[2].substringAfterLast('@')
    if (hostPort.startsWith("[")) return null
    val portText = hostPort.substringAfterLast(':', "")
    if (':' in hostPort && portText.toIntOrNull()?.takeIf { it in 1..65535 } == null) return null
    val raw = hostPort.substringBefore(':').lowercase().removeSuffix(".")
    val host = try { IDN.toASCII(raw).lowercase() } catch (_: IllegalArgumentException) { return null }
    val labels = host.split('.')
    if (!HOST_CHARS.matches(host) || labels.size < 2 || labels.any { it.isEmpty() } || labels.last().all(Char::isDigit)) return null
    if (host == "localhost" || listOf(".local", ".localhost", ".internal").any { host.endsWith(it) }) return null
    return IconTarget(host, "https://$host/")
}

fun iconHost(url: String): String? = iconTarget(url)?.host

/** An https URL on port 443, without user info, on a host [iconTarget] accepts. */
internal fun isAllowedIconUrl(url: String): Boolean {
    if (url.length > MAX_URL || !url.startsWith("https://", ignoreCase = true) || iconTarget(url) == null) return false
    val uri = try { URI(url) } catch (_: Exception) { return false }
    return (uri.port == -1 || uri.port == 443) && uri.rawUserInfo == null
}

/** The URL of redirect number [hop] from [from] to [location], or null past [MAX_REDIRECTS], off https or port 443, with user info or to a refused host. */
fun resolveRedirect(from: String, location: String, hop: Int): String? {
    if (hop > MAX_REDIRECTS) return null
    val next = try { URI(from).resolve(location.trim()).toString() } catch (_: Exception) { return null }
    return next.takeIf(::isAllowedIconUrl)
}

data class IconCandidate(val url: String, val kind: Kind, val size: Int?) {
    enum class Kind { Touch, Icon, Favicon }
}

private val ATTR = Regex("""([a-zA-Z_:][-a-zA-Z0-9_:.]*)\s*(?:=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'<>`]+)))?""")
private val SIZE = Regex("(\\d{1,5})x(\\d{1,5})", RegexOption.IGNORE_CASE)

/** The icon links of a page, resolved against [baseUrl] (the page's final URL), https only. SVG and `data:` are left out: they do not decode. */
fun parseIconLinks(html: String, baseUrl: String): List<IconCandidate> =
    linkTags(withoutComments(html)).take(MAX_LINK_TAGS).mapNotNull { tag ->
        val attrs = ATTR.findAll(tag).associate { m ->
            m.groupValues[1].lowercase() to (m.groupValues.drop(2).firstOrNull { it.isNotEmpty() } ?: "")
        }
        val rel = attrs["rel"].orEmpty().lowercase().split(Regex("\\s+"))
        val kind = when {
            "apple-touch-icon" in rel || "apple-touch-icon-precomposed" in rel -> IconCandidate.Kind.Touch
            "icon" in rel -> IconCandidate.Kind.Icon
            else -> return@mapNotNull null
        }
        if ("svg" in attrs["type"].orEmpty().lowercase()) return@mapNotNull null
        val href = attrs["href"].orEmpty().replace("&amp;", "&").trim()
        if (href.isEmpty() || href.length > MAX_URL || href.startsWith("data:", ignoreCase = true)) return@mapNotNull null
        if (href.substringBefore('?').substringBefore('#').endsWith(".svg", ignoreCase = true)) return@mapNotNull null
        val url = try { URI(baseUrl).resolve(href).toString() } catch (_: Exception) { return@mapNotNull null }
        if (!url.startsWith("https://", ignoreCase = true) || url.length > MAX_URL) return@mapNotNull null
        val size = attrs["sizes"]?.let { s -> SIZE.findAll(s).maxOfOrNull { maxOf(it.groupValues[1].toInt(), it.groupValues[2].toInt()) } }
        IconCandidate(url, kind, size)
    }.toList()

/** [html] without `<!-- … -->` comments; an unclosed one is kept. One pass: a regex here was quadratic on hostile pages. */
private fun withoutComments(html: String): String {
    val out = StringBuilder(html.length)
    var from = 0
    while (true) {
        val start = html.indexOf("<!--", from)
        val end = if (start < 0) -1 else html.indexOf("-->", start + 4)
        if (end < 0) return out.append(html, from, html.length).toString()
        out.append(html, from, start)
        from = end + 3
    }
}

/** The attribute text of each `<link …>` tag, in one pass; stops at a tag with no `>`. */
private fun linkTags(html: String): Sequence<String> = sequence {
    var from = 0
    while (true) {
        val start = html.indexOf("<link", from, ignoreCase = true)
        if (start < 0) return@sequence
        val body = start + 5
        if (body < html.length && (html[body].isLetterOrDigit() || html[body] == '_')) { from = body; continue }
        val end = html.indexOf('>', body)
        if (end < 0) return@sequence
        yield(html.substring(body, end))
        from = end + 1
    }
}

/** Touch icons, then icons, each largest first (document order breaks ties), then `/favicon.ico`; at most four. */
fun orderCandidates(links: List<IconCandidate>, rootUrl: String): List<IconCandidate> {
    val bySize = compareByDescending<IconCandidate> { it.size ?: 0 }
    val ordered = (links.filter { it.kind == IconCandidate.Kind.Touch }.sortedWith(bySize) +
        links.filter { it.kind == IconCandidate.Kind.Icon }.sortedWith(bySize)).distinctBy { it.url }
    val favicon = rootUrl + "favicon.ico"
    val first = ordered.take(MAX_ATTEMPTS)
    return if (first.any { it.url == favicon }) first else ordered.take(MAX_ATTEMPTS - 1) + IconCandidate(favicon, IconCandidate.Kind.Favicon, null)
}

/** Copy the source rectangle `src*` into the destination rectangle `dst*` of a [ICON_SIZE] square. */
data class IconPlan(
    val srcX: Int, val srcY: Int, val srcW: Int, val srcH: Int,
    val dstX: Int, val dstY: Int, val dstW: Int, val dstH: Int,
)

/**
 * An opaque source of at least 96 px is centre-cropped to fill the square. Anything else is fitted into 88 px and centred,
 * so a transparent margin of at least 4 px remains and the stored file never reads as opaque. Under 16 px is no icon.
 */
fun planIcon(srcW: Int, srcH: Int, opaque: Boolean): IconPlan? {
    val min = minOf(srcW, srcH)
    if (min < MIN_EDGE) return null
    if (opaque && min >= ICON_SIZE) return IconPlan((srcW - min) / 2, (srcH - min) / 2, min, min, 0, 0, ICON_SIZE, ICON_SIZE)
    val scale = SMALL_BOX.toFloat() / maxOf(srcW, srcH)
    val w = Math.round(srcW * scale).coerceIn(1, SMALL_BOX)
    val h = Math.round(srcH * scale).coerceIn(1, SMALL_BOX)
    return IconPlan(0, 0, srcW, srcH, (ICON_SIZE - w) / 2, (ICON_SIZE - h) / 2, w, h)
}

/** Every pixel fully opaque (ARGB). */
fun fillsTile(pixels: IntArray): Boolean = pixels.all { it ushr 24 == 0xFF }

/** The mean luminance of the square's outer ring is at least 0.92: the tile needs a border to stand out. */
fun hasLightEdge(pixels: IntArray, size: Int): Boolean {
    if (size <= 0 || pixels.size < size * size) return false
    val ring = (0 until size * size).filter { val x = it % size; val y = it / size; x == 0 || y == 0 || x == size - 1 || y == size - 1 }.map { pixels[it] }
    val mean = ring.sumOf { p ->
        0.2126 * (p shr 16 and 0xFF) + 0.7152 * (p shr 8 and 0xFF) + 0.0722 * (p and 0xFF)
    } / (ring.size * 255.0)
    return mean >= 0.92
}

/** Older than 30 days, or more than a day in the future (the clock went back). */
fun isStale(lastModifiedMillis: Long, nowMillis: Long): Boolean =
    nowMillis - lastModifiedMillis >= FRESH_MS || lastModifiedMillis > nowMillis + CLOCK_SKEW_MS

enum class ReadMode { Image, Html }

/**
 * Reads at most [max] bytes ([max] + 1 for an image, to tell that it is too big). An image over [max] gives null;
 * HTML is cut at [max] and stops once `</head>` or `<body` has been read.
 */
fun readBounded(input: InputStream, max: Int, mode: ReadMode): ByteArray? {
    val limit = if (mode == ReadMode.Image) max + 1 else max
    val out = ByteArrayOutputStream()
    val buf = ByteArray(8192)
    var tail = ""
    while (out.size() < limit) {
        val n = input.read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
        if (mode == ReadMode.Html) {
            val text = tail + String(buf, 0, n, Charsets.ISO_8859_1).lowercase()
            if ("</head>" in text || "<body" in text) break
            tail = text.takeLast(6)
        }
    }
    return if (mode == ReadMode.Image && out.size() > max) null else out.toByteArray()
}
