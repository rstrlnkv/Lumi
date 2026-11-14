package ru.digitalpunk.lumi.ui.settings

import ru.digitalpunk.lumi.ui.settings.SettingsPage.Root
import ru.digitalpunk.lumi.ui.settings.SettingsPage.SiteData
import ru.digitalpunk.lumi.ui.settings.SettingsPage.Sites
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SettingsNavigationTest {
    @Test fun `pushing adds on top and popping removes it`() {
        assertEquals(listOf(Root, Sites), listOf(Root).pushed(Sites).pushed(SiteData).popped())
    }

    @Test fun `popping the root closes Settings`() {
        assertNull(listOf(Root).popped())
    }

    @Test fun `pushing the page on top again changes nothing`() {
        assertEquals(listOf(Root, Sites), listOf(Root, Sites).pushed(Sites))
    }

    @Test fun `restoring unknown names gives the root`() {
        assertEquals(listOf(Root), restoreStack(listOf("Root", "Gone")))
        assertEquals(listOf(Root), restoreStack(emptyList()))
        assertEquals(listOf(Root), restoreStack(listOf("Sites")))
    }

    @Test fun `restoring keeps the known prefix`() {
        assertEquals(listOf(Root, Sites), restoreStack(listOf("Root", "Sites", "Gone", "SiteData")))
        assertEquals(listOf(Root, Sites, SiteData), restoreStack(listOf("Root", "Sites", "SiteData")))
    }
}
