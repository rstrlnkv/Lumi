package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoRuntimeSettings
import kotlin.test.Test
import kotlin.test.assertEquals

class PageThemeTest {
    @Test fun `system follows the device, light and dark are forced`() {
        assertEquals(GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM, geckoColorScheme(PageTheme.System))
        assertEquals(GeckoRuntimeSettings.COLOR_SCHEME_LIGHT, geckoColorScheme(PageTheme.Light))
        assertEquals(GeckoRuntimeSettings.COLOR_SCHEME_DARK, geckoColorScheme(PageTheme.Dark))
    }
}
