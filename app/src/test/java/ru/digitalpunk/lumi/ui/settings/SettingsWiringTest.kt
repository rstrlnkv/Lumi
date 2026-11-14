package ru.digitalpunk.lumi.ui.settings

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** The Settings host is Compose wiring with no pure seam beyond [SettingsNavigation], so these read the source. */
class SettingsWiringTest {
    private val host = File("src/main/java/ru/digitalpunk/lumi/ui/settings/SettingsScreen.kt").readText()
    private val screen = File("src/main/java/ru/digitalpunk/lumi/ui/BrowserScreen.kt").readText()

    @Test fun `one BackHandler pops the settings stack`() {
        assertEquals(1, Regex("""BackHandler\b""").findAll(host.substringAfter("@Composable")).count())
        assertTrue("popped()" in host)
        assertFalse("BackHandler(enabled = settingsOpen)" in screen)
    }

    @Test fun `the stack is saved`() {
        assertTrue("rememberSaveable(stateSaver = SettingsStackSaver)" in host)
    }

    @Test fun `only the top page is composed and the pages under it keep their state`() {
        assertTrue("AnimatedContent(" in host && "contentKey = { it.last() }" in host)
        assertTrue("SaveableStateProvider(page.name)" in host)
        assertTrue("removeState(top.name)" in host)
    }

    @Test fun `the default card shows only while Lumi is not the default`() {
        assertTrue("if (!isDefault) DefaultCard(" in host)
        assertTrue("if (isDefault) DefaultBrowserRow(" in host)
    }

    @Test fun `the snackbar sits above the navigation bar over Settings`() {
        assertTrue("libraryOpen || settingsOpen" in screen.lines().single { "val snackbarBottom =" in it })
    }
}
