package ru.digitalpunk.lumi.data.icons

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Literals only: `InetAddress.getByName` on a literal does no DNS lookup. */
class IconAddressTest {
    private fun public(literal: String) = isPublicAddress(InetAddress.getByName(literal))

    private fun assertPublic(vararg literals: String) = literals.forEach { assertTrue(public(it), it) }
    private fun assertRefused(vararg literals: String) = literals.forEach { assertFalse(public(it), it) }

    @Test fun eachPrivateOrReservedIpv4RangeIsRefusedOnBothEdgesAndItsNeighboursAreAllowed() {
        assertRefused("0.0.0.0", "0.255.255.255")
        assertPublic("1.0.0.0", "9.255.255.255")
        assertRefused("10.0.0.0", "10.255.255.255")
        assertPublic("11.0.0.0", "100.63.255.255")
        assertRefused("100.64.0.0", "100.127.255.255")
        assertPublic("100.128.0.0", "126.255.255.255")
        assertRefused("127.0.0.0", "127.255.255.255")
        assertPublic("128.0.0.0", "169.253.255.255")
        assertRefused("169.254.0.0", "169.254.255.255")
        assertPublic("169.255.0.0", "172.15.255.255")
        assertRefused("172.16.0.0", "172.31.255.255")
        assertPublic("172.32.0.0", "192.167.255.255")
        assertRefused("192.168.0.0", "192.168.255.255")
        assertPublic("192.169.0.0", "198.17.255.255")
        assertRefused("198.18.0.0", "198.19.255.255")
        assertPublic("198.20.0.0", "223.255.255.255")
        assertRefused("224.0.0.0", "239.255.255.255", "240.0.0.0", "255.255.255.255")
        assertPublic("8.8.8.8", "1.1.1.1")
    }

    @Test fun ipv4DocumentationAndProtocolRangesAreRefusedAndTheirNeighboursAllowed() {
        assertRefused("192.0.0.0", "192.0.0.255", "192.0.2.0", "192.0.2.255", "198.51.100.0", "198.51.100.255", "203.0.113.0", "203.0.113.255")
        assertPublic("192.0.1.0", "192.0.3.0", "198.51.99.255", "198.51.101.0", "203.0.112.255", "203.0.114.0")
    }

    @Test fun ipv6LocalMulticastAndOutsideGlobalUnicastAreRefused() {
        assertRefused(
            "::", "::1", "fe80::1", "febf:ffff::", "fc00::", "fdff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "fbff::", "fe00::",
            "fec0::1", "ff02::1", "4000::", "1fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff", "100::1",
        )
        assertPublic("2606:4700::1111", "2a00:1450:4001::200e", "2000::1", "3fff:ffff:ffff:ffff:ffff:ffff:ffff:ffff")
    }

    @Test fun teredoAndIpv6DocumentationAreRefusedAndTheirNeighboursAllowed() {
        assertRefused("2001:db8::1", "2001:db8:ffff::1", "2001:0:1::1", "2001::", "3fff::1", "3fff:fff::1")
        assertPublic("2001:db7::1", "2001:db9::1", "2001:1::1", "3fff:1000::1")
    }

    @Test fun embeddedIpv4FormsAreJudgedByTheirIpv4Address() {
        // `::ffff:a.b.c.d` literals come back from Java as Inet4Address; the raw 16 bytes are checked below as well.
        assertRefused("::ffff:10.0.0.1", "::ffff:127.0.0.1", "::ffff:192.168.1.1", "::ffff:169.254.1.1", "::ffff:0.0.0.0")
        assertPublic("::ffff:8.8.8.8")
        assertPublic("64:ff9b::8.8.8.8", "2002:0808:0808::1")
        assertRefused("64:ff9b::10.0.0.1", "64:ff9b::127.0.0.1", "64:ff9b:1::1", "2002:0a00:0001::1", "2002:7f00:0001::1", "::8.8.8.8")
    }

    @Test fun rawSixteenByteMappedFormsAreJudgedByTheirIpv4Address() {
        fun mapped(a: Int, b: Int, c: Int, d: Int) = ByteArray(16).also {
            it[10] = -1; it[11] = -1; it[12] = a.toByte(); it[13] = b.toByte(); it[14] = c.toByte(); it[15] = d.toByte()
        }
        assertFalse(isPublicAddress(mapped(127, 0, 0, 1)))
        assertFalse(isPublicAddress(mapped(192, 168, 1, 1)))
        assertTrue(isPublicAddress(mapped(8, 8, 8, 8)))
    }

    @Test fun anAddressOfAWrongSizeIsRefused() =
        listOf(0, 3, 5, 15, 17).forEach { assertFalse(isPublicAddress(ByteArray(it) { 8 }), "$it bytes") }

    @Test fun aHostIsUsableOnlyWhenEveryRecordIsPublic() {
        val good = listOf(InetAddress.getByName("8.8.8.8"), InetAddress.getByName("2606:4700::1111"))
        assertNull(pickPublicAddresses(emptyList()))
        assertNull(pickPublicAddresses(good + InetAddress.getByName("10.0.0.1")))
        assertNull(pickPublicAddresses(good + InetAddress.getByName("::1")))
        assertEquals(good, pickPublicAddresses(good))
    }
}
