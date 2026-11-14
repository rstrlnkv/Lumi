package ru.digitalpunk.lumi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import ru.digitalpunk.lumi.data.ThemeChoice
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThemeRulesTest {
    private fun contrast(a: Color, b: Color): Float {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05f) / (lo + 0.05f)
    }

    /** Every colour role of the scheme by name, read through its getters (Color is a value class, so they return a long). */
    private fun roles(scheme: ColorScheme): Map<String, Color> =
        ColorScheme::class.java.methods.filter { it.name.startsWith("get") && it.returnType == Long::class.javaPrimitiveType && it.parameterCount == 0 }
            .associate { it.name.removePrefix("get").substringBefore('-') to Color((it.invoke(scheme) as Long).toULong()) }

    @Test fun `system follows the system, light and dark are fixed`() {
        assertTrue(isDark(ThemeChoice.System, systemDark = true))
        assertFalse(isDark(ThemeChoice.System, systemDark = false))
        assertFalse(isDark(ThemeChoice.Light, systemDark = true))
        assertTrue(isDark(ThemeChoice.Dark, systemDark = false))
    }

    @Test fun `wallpaper colours only from Android 12 and only when on`() {
        assertFalse(usesWallpaper(30, true))
        assertFalse(usesWallpaper(31, false))
        assertTrue(usesWallpaper(31, true))
    }

    @Test fun `monochrome primary is black in light and white in dark`() {
        assertEquals(Color.Black, MonochromeLight.primary)
        assertEquals(Color.White, MonochromeDark.primary)
    }

    @Test fun `monochrome has no colour`() {
        val errorRoles = setOf("Error", "OnError", "ErrorContainer", "OnErrorContainer")
        listOf(MonochromeLight, MonochromeDark).forEach { scheme ->
            val all = roles(scheme)
            assertTrue(all.size >= 40, "found ${all.size} roles")
            all.filterKeys { it !in errorRoles }.forEach { (name, color) ->
                val argb = color.toArgb()
                val r = argb shr 16 and 0xFF
                val g = argb shr 8 and 0xFF
                val b = argb and 0xFF
                assertTrue(r == g && g == b, "$name is #%06X".format(argb and 0xFFFFFF))
            }
        }
    }

    @Test fun `monochrome text contrasts with its surfaces`() {
        listOf(MonochromeLight, MonochromeDark).forEach { s ->
            listOf(s.surface, s.surfaceBright, s.surfaceContainer).forEach {
                assertTrue(contrast(s.onSurface, it) >= 4.5f)
                assertTrue(contrast(s.onSurfaceVariant, it) >= 4.5f)
            }
        }
    }
}
