package ru.digitalpunk.lumi.updates

import ru.digitalpunk.lumi.about.Channel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReleaseTagTest {
    private fun tag(s: String) = parseTag(s)!!

    @Test fun `a DEV tag gives version, channel and build`() =
        assertEquals(ReleaseTag(0, 1, 0, Channel.DEV, 3613543), parseTag("v0.1.0.DEV.3613543"))

    @Test fun `a STABLE tag has no build`() = assertEquals(ReleaseTag(1, 2, 3, Channel.STABLE, null), parseTag("v1.2.3"))

    @Test fun `old, lower-case, unprefixed, cut or oversize tags are not release tags`() {
        listOf("v0.1.0-dev.1", "0.1.0.DEV.1", "v0.1.0.dev.1", "v0.1.0.DEV.", "v99999999999.0.0", "v1.2.3.RC.4", "").forEach {
            assertNull(parseTag(it), it)
        }
    }

    @Test fun `a higher version wins over any channel`() = assertTrue(tag("v0.2.0.DEV.1") > tag("v0.1.0"))

    @Test fun `a STABLE release is above the DEV and BETA builds of its version`() {
        assertTrue(tag("v0.1.0") > tag("v0.1.0.BETA.9"))
        assertTrue(tag("v0.1.0") > tag("v0.1.0.DEV.9999999"))
    }

    @Test fun `within one version the higher build wins`() {
        assertTrue(tag("v0.1.0.BETA.9") > tag("v0.1.0.DEV.5"))
        assertTrue(tag("v0.1.0.DEV.5") > tag("v0.1.0.DEV.4"))
    }

    // Build numbers are commit counts (a few dozen to thousands), compared as numbers, not as text.
    @Test fun `a commit count of two digits is above one of one digit and the installed build is compared with it`() {
        assertTrue(tag("v0.1.0.DEV.51") > tag("v0.1.0.DEV.9"))
        assertTrue(tag("v0.1.0.DEV.51") > installedTag("0.1.0", Channel.DEV, 50)!!)
    }

    @Test fun `display reads as the About screen writes it`() {
        assertEquals("0.1.0 DEV (Build 3613543)", tag("v0.1.0.DEV.3613543").display)
        assertEquals("1.2.3", tag("v1.2.3").display)
    }

    @Test fun `each channel sees itself and the more stable ones`() {
        assertTrue(Channel.STABLE.sees(Channel.STABLE))
        assertFalse(Channel.STABLE.sees(Channel.BETA))
        assertFalse(Channel.STABLE.sees(Channel.DEV))
        assertTrue(Channel.BETA.sees(Channel.STABLE))
        assertTrue(Channel.BETA.sees(Channel.BETA))
        assertFalse(Channel.BETA.sees(Channel.DEV))
        Channel.entries.forEach { assertTrue(Channel.DEV.sees(it)) }
    }

    @Test fun `the installed build reads like its tag`() {
        assertEquals(tag("v0.1.0.DEV.3613543"), installedTag("0.1.0", Channel.DEV, 3613543))
        assertEquals(ReleaseTag(1, 0, 0, Channel.STABLE, 3613543), installedTag("1.0.0", Channel.STABLE, 3613543))
        assertNull(installedTag("0.1.0-dev.1", Channel.DEV, 27))
    }
}
