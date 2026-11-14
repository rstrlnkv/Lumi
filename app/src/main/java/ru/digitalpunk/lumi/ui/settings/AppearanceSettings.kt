package ru.digitalpunk.lumi.ui.settings

import android.os.Build
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.data.AddressBarStyle
import ru.digitalpunk.lumi.data.SettingsStore
import ru.digitalpunk.lumi.data.ThemeChoice

/**
 * S2 Appearance: Theme, Wallpaper colours (Android 12+ only, where Dynamic Color exists) and the address bar style,
 * the same stored value as the More menu's switch.
 */
@Composable
fun AppearanceSettings(settings: SettingsStore, scope: CoroutineScope, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val theme by settings.theme.collectAsStateWithLifecycle(ThemeChoice.System)
    val wallpaper by settings.wallpaperColors.collectAsStateWithLifecycle(true)
    val bar by settings.addressBarStyle.collectAsStateWithLifecycle(AddressBarStyle.Compact)
    SettingsPageScaffold(stringResource(R.string.settings_appearance), onBack, modifier) {
        SettingsGroup {
            val themes = listOf(ThemeChoice.System, ThemeChoice.Light, ThemeChoice.Dark)
            SettingsChoiceRow(
                stringResource(R.string.settings_theme), painterResource(R.drawable.ic_contrast),
                listOf(stringResource(R.string.settings_theme_system), stringResource(R.string.settings_theme_light), stringResource(R.string.settings_theme_dark)),
                themes.indexOf(theme),
            ) { scope.launch { settings.setTheme(themes[it]) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                SettingsRow(
                    stringResource(R.string.settings_wallpaper_colors),
                    stringResource(if (wallpaper) R.string.settings_wallpaper_colors_on else R.string.settings_wallpaper_colors_off),
                    painterResource(R.drawable.ic_wallpaper),
                    Modifier.toggleable(value = wallpaper, role = Role.Switch) { scope.launch { settings.setWallpaperColors(it) } },
                ) { Switch(checked = wallpaper, onCheckedChange = null) }
            }
        }
        SettingsGroup {
            val styles = listOf(AddressBarStyle.Classic, AddressBarStyle.Compact)
            SettingsChoiceRow(
                stringResource(R.string.settings_address_bar), painterResource(R.drawable.ic_bottom_navigation),
                listOf(stringResource(R.string.settings_address_bar_classic), stringResource(R.string.settings_address_bar_compact)),
                styles.indexOf(bar),
            ) { scope.launch { settings.setAddressBarStyle(styles[it]) } }
        }
    }
}
