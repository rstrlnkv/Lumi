package ru.digitalpunk.lumi.engine

import java.net.URL

/** Chrome's address spaces (ADR 015): loopback is local, the LAN ranges are private, everything else is public. */
enum class AddressSpace { Local, Private, Public }

/**
 * The space of a 4- or 16-byte address; null for any other length. 240.0.0.0/4 and 198.18.0.0/15 stay public: a VPN
 * answers names with them (ADR 014). An IPv4-mapped IPv6 address is judged by its IPv4 part; NAT64 is not in the table.
 */
fun addressSpace(addr: ByteArray): AddressSpace? = when (addr.size) {
    4 -> v4Space(addr[0].toInt() and 0xff, addr[1].toInt() and 0xff)
    16 -> {
        val head = (0 until 10).all { addr[it].toInt() == 0 }
        when {
            head && addr[10].toInt() == -1 && addr[11].toInt() == -1 -> v4Space(addr[12].toInt() and 0xff, addr[13].toInt() and 0xff)
            head && (10 until 15).all { addr[it].toInt() == 0 } && addr[15].toInt() in 0..1 -> AddressSpace.Local
            (addr[0].toInt() and 0xfe) == 0xfc -> AddressSpace.Private
            (addr[0].toInt() and 0xff) == 0xfe && (addr[1].toInt() and 0xc0) == 0x80 -> AddressSpace.Private
            else -> AddressSpace.Public
        }
    }
    else -> null
}

private fun v4Space(a: Int, b: Int): AddressSpace = when {
    a == 127 || a == 0 -> AddressSpace.Local
    a == 10 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254) || (a == 100 && b in 64..127) -> AddressSpace.Private
    else -> AddressSpace.Public
}

/** Lumi's own fetch goes out only when every address the name resolves to is public; no address at all refuses it too. */
fun refuseFetch(addresses: List<ByteArray>): Boolean = addresses.isEmpty() || addresses.any { addressSpace(it) != AddressSpace.Public }

/**
 * The ASCII host name to resolve for an https URL; null for another scheme, an IP literal, or anything Gecko might read
 * as another host than Java does: user info, a backslash, a percent sign or a non-ASCII character in the host, and a
 * control character anywhere. The caller fetches this exact string (ADR 015).
 */
internal fun fetchableHost(url: String): String? {
    if (url.any { it == '\\' || it.isISOControl() }) return null
    val host = HTTPS_URL.matchEntire(url)?.groupValues?.get(1) ?: return null
    // No top-level domain starts with a digit, so this catches dotted, decimal and hex IPv4; the pattern takes no IPv6 brackets.
    val last = host.trimEnd('.').substringAfterLast('.')
    if (last.isEmpty() || last[0].isDigit()) return null
    return host
}

private val HTTPS_URL = Regex("""https://([a-z0-9-]+(?:\.[a-z0-9-]+)*\.?)(?::[0-9]{1,5})?(?:[/?#].*)?""", RegexOption.IGNORE_CASE)

/** The next URL of a redirect from [current] to [location] (relative or absolute), if it passes [fetchableHost]. */
internal fun nextHop(current: String, location: String?): String? {
    if (location.isNullOrBlank()) return null
    val next = runCatching { URL(URL(current), location).toString() }.getOrNull() ?: return null
    return next.takeIf { fetchableHost(it) != null }
}

internal val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

/** Chrome's own redirect limit is 20; Lumi's icons and suggestions need far fewer. */
internal const val MAX_REDIRECTS = 5
