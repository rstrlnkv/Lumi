package ru.digitalpunk.lumi.privacy

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The private look: the baseline dark scheme, fixed. It never follows the system theme or Dynamic Color and never
 * uses `inverseSurface` (light in dark theme), so the private bar and cards are dark in both themes.
 */
val PrivateBarScheme: ColorScheme = darkColorScheme()

/** The scheme the bar and a card are drawn with. */
fun barScheme(base: ColorScheme, private: Boolean): ColorScheme = if (private) PrivateBarScheme else base

/** True under the bar of a private tab: the site icon becomes the mask and TalkBack says "Private tab". */
val LocalPrivateTab = staticCompositionLocalOf { false }
