package ru.digitalpunk.lumi.ui

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/**
 * The page's colour behind the transparent status bar, or the app surface (the root Surface's colour, so nothing shows)
 * when [tint] is null. A Compose band, not `window.statusBarColor`: that has no effect from API 35 (see docs:
 * 002-bar-offset-owned-by-compose).
 */
@Composable
fun BoxScope.StatusBarBand(tint: Color?) {
    val color by animateColorAsState(tint ?: MaterialTheme.colorScheme.surface, tween(150), label = "statusBand")
    Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars).background(color))
}

/** While [tint] is set, the status-bar icons are black or white, whichever contrasts more with it; else as they were. */
@Composable
fun StatusBarIcons(tint: Color?) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    val argb = tint?.toArgb()
    DisposableEffect(window, argb) {
        val controller = WindowCompat.getInsetsController(window, view)
        val before = controller.isAppearanceLightStatusBars
        if (argb != null) controller.isAppearanceLightStatusBars = darkIconsOn(argb)
        onDispose { controller.isAppearanceLightStatusBars = before }
    }
}
