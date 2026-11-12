package ru.digitalpunk.lumi.privacy

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class PrivateBarColorsTest {
    private val light = lightColorScheme()
    private val dark = darkColorScheme(surfaceContainerLowest = Color(0xFF223344))

    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    @Test fun privateBarColoursAreTheSameInLightAndDarkTheme() {
        assertEquals(barScheme(light, private = true), barScheme(dark, private = true))
        assertEquals(light, barScheme(light, private = false))
    }

    @Test fun privateBarContainerIsDark() {
        val s = barScheme(light, private = true)
        assertTrue(s.surfaceContainerLowest.luminance() < 0.05f)
        assertTrue(s.surface.luminance() < 0.05f)
        assertNotEquals(light.inverseSurface, s.surfaceContainerLowest)
    }

    @Test fun privateTextContrastsWithContainer() {
        val s = barScheme(light, private = true)
        listOf(s.surface, s.surfaceContainerLowest, s.surfaceContainerHigh).forEach {
            assertTrue(contrast(s.onSurface, it) >= 4.5f)
            assertTrue(contrast(s.onSurfaceVariant, it) >= 4.5f)
        }
    }

    @Test fun privateFieldDiffersFromContainer() {
        val s = barScheme(light, private = true)
        assertNotEquals(s.surfaceContainerLowest, s.surfaceContainerHigh)
    }
}
