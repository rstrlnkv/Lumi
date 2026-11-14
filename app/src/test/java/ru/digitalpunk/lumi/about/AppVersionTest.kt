package ru.digitalpunk.lumi.about

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppVersionTest {
    @Test fun `a dev prerelease is a dev build`() = assertTrue(AppVersion("0.1.0-dev.42", 318).isDev)

    @Test fun `a release, a beta-looking suffix or dev mid-string is not a dev build`() {
        assertFalse(AppVersion("0.1.0", 1).isDev)
        assertFalse(AppVersion("0.1.0-beta.1", 1).isDev)
        assertFalse(AppVersion("0.1.0-development", 1).isDev)
        assertFalse(AppVersion("0.1.0-dev.1x", 1).isDev)
    }

    @Test fun `the copied text is version then build in parentheses`() =
        assertEquals("0.1.0-dev.42 (318)", AppVersion("0.1.0-dev.42", 318).copyText)

    @Test fun `the Copied snackbar shows only before the system clipboard confirmation of API 33`() {
        listOf(26, 30, 32).forEach { assertTrue(showCopiedSnackbar(it), "API $it") }
        listOf(33, 34).forEach { assertFalse(showCopiedSnackbar(it), "API $it") }
    }
}
