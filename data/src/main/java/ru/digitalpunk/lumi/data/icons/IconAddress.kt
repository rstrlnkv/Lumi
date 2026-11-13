package ru.digitalpunk.lumi.data.icons

import java.net.InetAddress

/**
 * True for an address an icon fetch may connect to: public unicast only. Refused: loopback, any-local, link-local,
 * private, CGNAT, ULA, multicast, reserved, documentation, Teredo, and the IPv4-mapped, NAT64 and 6to4 forms of these.
 */
internal fun isPublicAddress(bytes: ByteArray): Boolean {
    val b = IntArray(bytes.size) { bytes[it].toInt() and 0xFF }
    return when (b.size) {
        4 -> isPublicV4(b[0], b[1], b[2])
        16 -> isPublicV6(b)
        else -> false
    }
}

internal fun isPublicAddress(a: InetAddress) = isPublicAddress(a.address)

/** [all] when it is not empty and every record is public; a single local record makes the whole host refused. */
internal fun pickPublicAddresses(all: List<InetAddress>): List<InetAddress>? =
    all.takeIf { it.isNotEmpty() && it.all(::isPublicAddress) }

private fun isPublicV4(b0: Int, b1: Int, b2: Int): Boolean = when {
    b0 == 0 || b0 == 10 || b0 == 127 || b0 >= 224 -> false
    b0 == 100 && b1 in 64..127 -> false
    b0 == 169 && b1 == 254 -> false
    b0 == 172 && b1 in 16..31 -> false
    b0 == 192 && b1 == 168 -> false
    b0 == 192 && b1 == 0 && (b2 == 0 || b2 == 2) -> false
    b0 == 198 && (b1 == 18 || b1 == 19) -> false
    b0 == 198 && b1 == 51 && b2 == 100 -> false
    b0 == 203 && b1 == 0 && b2 == 113 -> false
    else -> true
}

private fun isPublicV6(b: IntArray): Boolean {
    fun zero(range: IntRange) = range.all { b[it] == 0 }
    fun v4At(i: Int) = isPublicV4(b[i], b[i + 1], b[i + 2])
    return when {
        zero(0..9) && b[10] == 0xFF && b[11] == 0xFF -> v4At(12)
        b[0] == 0 && b[1] == 0x64 && b[2] == 0xFF && b[3] == 0x9B && zero(4..11) -> v4At(12)
        b[0] ushr 5 != 1 -> false
        b[0] == 0x20 && b[1] == 0x01 && b[2] == 0 && b[3] == 0 -> false
        b[0] == 0x20 && b[1] == 0x01 && b[2] == 0x0D && b[3] == 0xB8 -> false
        b[0] == 0x3F && b[1] == 0xFF && b[2] and 0xF0 == 0 -> false
        b[0] == 0x20 && b[1] == 0x02 -> v4At(2)
        else -> true
    }
}
