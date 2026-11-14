package ru.digitalpunk.lumi.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** A neutral grey of the given Material tone (L* 0–100), as Material Color Utilities' `SchemeMonochrome` uses them. */
private object Tone {
    val T0 = Color(0xFF000000)
    val T4 = Color(0xFF0E0E0E)
    val T6 = Color(0xFF131313)
    val T10 = Color(0xFF1B1B1B)
    val T12 = Color(0xFF1F1F1F)
    val T17 = Color(0xFF2A2A2A)
    val T20 = Color(0xFF303030)
    val T22 = Color(0xFF353535)
    val T24 = Color(0xFF393939)
    val T25 = Color(0xFF3B3B3B)
    val T30 = Color(0xFF474747)
    val T40 = Color(0xFF5E5E5E)
    val T49 = Color(0xFF747474)
    val T50 = Color(0xFF777777)
    val T60 = Color(0xFF919191)
    val T70 = Color(0xFFABABAB)
    val T80 = Color(0xFFC6C6C6)
    val T85 = Color(0xFFD4D4D4)
    val T87 = Color(0xFFDADADA)
    val T90 = Color(0xFFE2E2E2)
    val T92 = Color(0xFFE8E8E8)
    val T94 = Color(0xFFEEEEEE)
    val T95 = Color(0xFFF1F1F1)
    val T96 = Color(0xFFF3F3F3)
    val T98 = Color(0xFFF9F9F9)
    val T100 = Color(0xFFFFFFFF)
}

/** The light palette with wallpaper colours off (and always on Android 8–11): greys with a black accent, baseline error colours. */
val MonochromeLight: ColorScheme = with(Tone) {
    lightColorScheme(
        primary = T0, onPrimary = T90, primaryContainer = T25, onPrimaryContainer = T100, inversePrimary = T80,
        secondary = T40, onSecondary = T100, secondaryContainer = T85, onSecondaryContainer = T10,
        tertiary = T25, onTertiary = T90, tertiaryContainer = T49, onTertiaryContainer = T100,
        background = T98, onBackground = T10, surface = T98, onSurface = T10, surfaceVariant = T90, onSurfaceVariant = T30,
        surfaceTint = T0, inverseSurface = T20, inverseOnSurface = T95,
        error = Color(0xFFBA1A1A), onError = Color(0xFFFFFFFF), errorContainer = Color(0xFFFFDAD6), onErrorContainer = Color(0xFF93000A),
        outline = T50, outlineVariant = T80, scrim = T0,
        surfaceBright = T98, surfaceContainer = T94, surfaceContainerHigh = T92, surfaceContainerHighest = T90,
        surfaceContainerLow = T96, surfaceContainerLowest = T100, surfaceDim = T87,
        primaryFixed = T40, primaryFixedDim = T30, onPrimaryFixed = T100, onPrimaryFixedVariant = T90,
        secondaryFixed = T80, secondaryFixedDim = T70, onSecondaryFixed = T10, onSecondaryFixedVariant = T25,
        tertiaryFixed = T40, tertiaryFixedDim = T30, onTertiaryFixed = T100, onTertiaryFixedVariant = T90,
    )
}

/** The dark palette with wallpaper colours off, and the scheme of private tabs then: greys with a white accent. */
val MonochromeDark: ColorScheme = with(Tone) {
    darkColorScheme(
        primary = T100, onPrimary = T10, primaryContainer = T85, onPrimaryContainer = T0, inversePrimary = T40,
        secondary = T80, onSecondary = T20, secondaryContainer = T30, onSecondaryContainer = T90,
        tertiary = T90, onTertiary = T10, tertiaryContainer = T60, onTertiaryContainer = T0,
        background = T6, onBackground = T90, surface = T6, onSurface = T90, surfaceVariant = T30, onSurfaceVariant = T80,
        surfaceTint = T100, inverseSurface = T90, inverseOnSurface = T20,
        error = Color(0xFFFFB4AB), onError = Color(0xFF690005), errorContainer = Color(0xFF93000A), onErrorContainer = Color(0xFFFFDAD6),
        outline = T60, outlineVariant = T30, scrim = T0,
        surfaceBright = T24, surfaceContainer = T12, surfaceContainerHigh = T17, surfaceContainerHighest = T22,
        surfaceContainerLow = T10, surfaceContainerLowest = T4, surfaceDim = T6,
        primaryFixed = T40, primaryFixedDim = T30, onPrimaryFixed = T100, onPrimaryFixedVariant = T90,
        secondaryFixed = T80, secondaryFixedDim = T70, onSecondaryFixed = T10, onSecondaryFixedVariant = T25,
        tertiaryFixed = T40, tertiaryFixedDim = T30, onTertiaryFixed = T100, onTertiaryFixedVariant = T90,
    )
}
