package ru.digitalpunk.lumi.privacy

import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The scheme the bar and a card are drawn with. A private one takes [privateScheme], the dark scheme of the current
 * palette (`LocalPrivateScheme`), so it is dark in both themes; it never uses `inverseSurface` (light in dark theme).
 */
fun barScheme(base: ColorScheme, private: Boolean, privateScheme: ColorScheme): ColorScheme = if (private) privateScheme else base

/** True under the bar of a private tab: the site icon becomes the mask and TalkBack says "Private tab". */
val LocalPrivateTab = staticCompositionLocalOf { false }

/** The app's own scheme, kept under the private bar's scheme so the bar's menus look the same in every tab. */
val LocalAppColorScheme = staticCompositionLocalOf<ColorScheme?> { null }
