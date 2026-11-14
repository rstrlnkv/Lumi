package ru.digitalpunk.lumi.engine

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import ru.digitalpunk.lumi.engine.AddressSpace.Local
import ru.digitalpunk.lumi.engine.AddressSpace.Private
import ru.digitalpunk.lumi.engine.AddressSpace.Public

/** Inputs NetAddressTest does not feed: near-miss IPv6 prefixes, mapped link-local, host spellings Gecko decodes. */
class NetAddressEdgeTest {
    private fun ip(text: String): ByteArray = InetAddress.getByName(text).address
    private fun v6(vararg tail: Int) = ByteArray(16).also { b -> tail.forEachIndexed { i, v -> b[16 - tail.size + i] = v.toByte() } }

    @Test fun anAddressWhoseTenthByteIsNotFfIsNotMappedAndStaysPublic() {
        // ::ff00:c0a8:101 has ff at 10 but 00 at 11: not ::ffff:192.168.1.1.
        assertEquals(Public, addressSpace(v6(0xff, 0x00, 192, 168, 1, 1)))
        assertEquals(Public, addressSpace(v6(0x00, 0xff, 192, 168, 1, 1)))
    }

    @Test fun anAddressWithAnyNonZeroByteInTheFirstTenIsNotMapped() {
        val b = v6(0xff, 0xff, 192, 168, 1, 1)
        b[9] = 1
        assertEquals(Public, addressSpace(b))
        val c = v6(0xff, 0xff, 127, 0, 0, 1)
        c[0] = 1
        assertEquals(Public, addressSpace(c))
    }

    @Test fun onlyColonColonAndColonColonOneAreLocalAmongTheLowAddresses() {
        assertEquals(Local, addressSpace(v6(0, 0)))
        assertEquals(Local, addressSpace(v6(1)))
        assertEquals(Public, addressSpace(v6(1, 1))) // ::101, byte 14 set
        assertEquals(Public, addressSpace(v6(1, 0, 0)))
        assertEquals(Public, addressSpace(v6(0xff)))
    }

    @Test fun mappedLinkLocalAndCarrierGradeNatAreLanAndMappedLoopbackRefusesTheFetch() {
        fun mapped(v4: String) = ByteArray(10) + byteArrayOf(-1, -1) + ip(v4)
        assertEquals(Private, addressSpace(mapped("169.254.169.254")))
        assertEquals(Private, addressSpace(mapped("100.64.0.1")))
        assertEquals(Private, addressSpace(mapped("172.16.0.1")))
        assertEquals(Local, addressSpace(mapped("0.0.0.0")))
        assertTrue(refuseFetch(listOf(mapped("127.0.0.1"))))
        assertTrue(refuseFetch(listOf(mapped("169.254.169.254"))))
    }

    @Test fun hostNamesThatNeedDecodingOrCarryUserInfoAreNotTakenForPublicNames() {
        assertNull(fetchableHost("https://user@192.168.1.1/"))
        assertNull(fetchableHost("https://example.com:443@192.168.1.1/"))
        assertNull(fetchableHost("https://192.168.1.1?@example.com/"))
        assertNull(fetchableHost("https://127.0.0.1:8443/"))
        assertEquals("localhost", fetchableHost("https://localhost/")) // refused later: it resolves to loopback
    }

    // Gecko percent-decodes a host: https://192.168.1.%31/ is 192.168.1.1. Java's URL keeps the %31, so the last label
    // does not start with a digit and the literal check is passed; only the later resolve stands in the way.
    @Test fun aPercentEncodedIpLiteralIsRefusedBeforeResolving() {
        assertNull(fetchableHost("https://192.168.1.%31/"))
        assertNull(fetchableHost("https://%31%39%32.168.1.%31/"))
        assertNull(fetchableHost("https://127.0.0.%31/favicon.ico"))
    }

    @Test fun aRedirectToAPercentEncodedIpLiteralIsRefused() {
        assertNull(nextHop("https://example.com/", "https://192.168.1.%31/admin"))
        assertNull(nextHop("https://example.com/", "//10.0.0.%31/"))
    }
}
