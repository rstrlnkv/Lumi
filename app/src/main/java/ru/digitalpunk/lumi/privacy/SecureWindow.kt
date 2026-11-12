package ru.digitalpunk.lumi.privacy

import android.view.WindowManager
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import ru.digitalpunk.lumi.engine.PRIVATE_GROUP
import ru.digitalpunk.lumi.engine.TabsState
import ru.digitalpunk.lumi.engine.isPrivate

/** A private tab in front, the grid on the Private chip, or a private tab waiting for unlock: blank recents card, no screenshots. */
fun needsSecureWindow(state: TabsState, gridOpen: Boolean): Boolean =
    state.tabs.any { it.id == state.selectedId && it.isPrivate } ||
        (gridOpen && state.currentGroup == PRIVATE_GROUP) ||
        state.privateResume

/** Sets `FLAG_SECURE` while [secure]; while [darkBar], the navigation bar icons are light so they read on the dark private bar. */
@Composable
fun SecureWindow(secure: Boolean, darkBar: Boolean) {
    val window = LocalActivity.current?.window ?: return
    DisposableEffect(window, secure) {
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    val view = LocalView.current
    DisposableEffect(window, darkBar) {
        val controller = WindowCompat.getInsetsController(window, view)
        val before = controller.isAppearanceLightNavigationBars
        if (darkBar) controller.isAppearanceLightNavigationBars = false
        onDispose { controller.isAppearanceLightNavigationBars = before }
    }
}
