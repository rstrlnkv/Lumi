package ru.digitalpunk.lumi.ui.theme

import android.annotation.SuppressLint
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import ru.digitalpunk.lumi.data.ThemeChoice

/** True when Lumi is dark: the chosen theme, or the device's for [ThemeChoice.System]. */
fun isDark(theme: ThemeChoice, systemDark: Boolean): Boolean = when (theme) {
    ThemeChoice.System -> systemDark
    ThemeChoice.Light -> false
    ThemeChoice.Dark -> true
}

/** Dynamic Color exists from Android 12; below it, and with the switch off, Lumi is monochrome. */
fun usesWallpaper(sdk: Int, wallpaperColors: Boolean): Boolean = sdk >= Build.VERSION_CODES.S && wallpaperColors

/** Lumi's dark or light, for code that sets window colours itself; never the device's setting when a theme is forced. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/** The dark scheme of the current palette: private tabs' bars and cards always use it, in both themes. */
val LocalPrivateScheme = staticCompositionLocalOf { MonochromeDark }

/** The chosen theme with Dynamic Color, or the monochrome palette when wallpaper colours are off or not there. */
@SuppressLint("NewApi") // usesWallpaper checks the SDK level.
@Composable
fun LumiTheme(theme: ThemeChoice, wallpaperColors: Boolean, content: @Composable () -> Unit) {
    val dark = isDark(theme, isSystemInDarkTheme())
    val context = LocalContext.current
    val wallpaper = usesWallpaper(Build.VERSION.SDK_INT, wallpaperColors)
    val darkScheme: ColorScheme = if (wallpaper) dynamicDarkColorScheme(context) else MonochromeDark
    val colors = when {
        dark -> darkScheme
        wallpaper -> dynamicLightColorScheme(context)
        else -> MonochromeLight
    }
    CompositionLocalProvider(LocalDarkTheme provides dark, LocalPrivateScheme provides darkScheme) {
        MaterialTheme(colorScheme = colors, content = content)
    }
}
