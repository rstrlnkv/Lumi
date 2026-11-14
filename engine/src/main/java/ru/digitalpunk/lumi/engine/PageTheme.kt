package ru.digitalpunk.lumi.engine

import org.mozilla.geckoview.GeckoRuntimeSettings

/** The colour scheme pages see through `prefers-color-scheme`: the device's, or forced light or dark. */
enum class PageTheme { System, Light, Dark }

internal fun geckoColorScheme(theme: PageTheme): Int = when (theme) {
    PageTheme.System -> GeckoRuntimeSettings.COLOR_SCHEME_SYSTEM
    PageTheme.Light -> GeckoRuntimeSettings.COLOR_SCHEME_LIGHT
    PageTheme.Dark -> GeckoRuntimeSettings.COLOR_SCHEME_DARK
}
