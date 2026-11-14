package ru.digitalpunk.lumi.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppVersionTest {
    @Test fun `the copied text is version, channel and build`() {
        assertEquals("0.1.0 DEV (Build 3613543)", AppVersion("0.1.0", 3613543, Channel.DEV).copyText)
        assertEquals("0.1.0 BETA (Build 3613543)", AppVersion("0.1.0", 3613543, Channel.BETA).copyText)
        assertEquals("0.1.0 STABLE (Build 3613543)", AppVersion("0.1.0", 3613543, Channel.STABLE).copyText)
    }

    @Test fun `a stable build shows the build number only after a tap`() {
        val stable = AppVersion("1.0.0", 3613543, Channel.STABLE)
        assertEquals("1.0.0", stable.shown(revealed = false))
        assertEquals("1.0.0 STABLE (Build 3613543)", stable.shown(revealed = true))
    }

    @Test fun `a dev or beta build always shows the build number`() {
        listOf(Channel.DEV, Channel.BETA).forEach { channel ->
            val v = AppVersion("0.1.0", 7, channel)
            assertEquals(v.copyText, v.shown(revealed = false))
        }
    }

    @Test fun `the channel name from the build is read exactly`() {
        assertEquals(Channel.DEV, Channel.of("DEV"))
        assertEquals(Channel.BETA, Channel.of("BETA"))
        assertEquals(Channel.STABLE, Channel.of("STABLE"))
    }

    @Test fun `the Copied snackbar shows only before the system clipboard confirmation of API 33`() {
        listOf(26, 30, 32).forEach { assertTrue(showCopiedSnackbar(it), "API $it") }
        listOf(33, 34).forEach { assertFalse(showCopiedSnackbar(it), "API $it") }
    }
}
