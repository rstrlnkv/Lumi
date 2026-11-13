package ru.digitalpunk.lumi.data.icons

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Inputs the first address test left out: each one flips a mutant of the IPv6 rules that stayed green. */
class IconAddressGapTest {
    private fun public(literal: String) = isPublicAddress(InetAddress.getByName(literal))

    @Test fun globalUnicastNextToThe3fffDocumentationRangeIsAllowed() {
        // 3fff::/20 is documentation; 3f00::/16 and 3ffe::/16 are not.
        listOf("3f00::1", "3ffe::1", "3ffe:ffff::1", "3eff::1").forEach { assertTrue(public(it), it) }
        listOf("3fff::1", "3fff:0fff::1").forEach { assertFalse(public(it), it) }
    }

    @Test fun a6to4AddressIsJudgedByTheIpv4AddressInItsSecondToFifthByte() {
        // 2002:c0a8:0101:: embeds 192.168.1.1; a rule that skips the first embedded byte sees 168.1.1 and allows it.
        listOf("2002:c0a8:0101::1", "2002:ac10:0001::1", "2002:6440:0001::1", "2002:a9fe:a9fe::1", "2002:7f00:0001::1")
            .forEach { assertFalse(public(it), it) }
        assertTrue(public("2002:0808:0808::1"))
    }

    @Test fun nat64WithAPublicLookingTailButNotTheWellKnownPrefixIsRefused() {
        // 64:ff9b:1::/48 is local-use NAT64: only 64:ff9b::/96 is judged by its IPv4 tail.
        listOf("64:ff9b:1::808:808", "64:ff9b::1:808:808", "64:ff9a::808:808").forEach { assertFalse(public(it), it) }
        assertTrue(public("64:ff9b::808:808"))
    }
}
