package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.data.AddressBarStyle
import ru.digitalpunk.lumi.engine.BackHistory
import ru.digitalpunk.lumi.ui.tabgrid.tabCountGlyph

@Composable
private fun barIconColors() = IconButtonDefaults.iconButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)

/** Back; a long press lists the tab's back history, and a pick goes to that entry. */
@Composable
fun BackButton(enabled: Boolean, onClick: () -> Unit, history: BackHistory, onPick: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        LongPressIconButton(
            onClick = onClick,
            onLongClick = { if (history.hasBack) open = true },
            longClickLabel = stringResource(R.string.cd_long_press_back),
            enabled = enabled,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back)) }
        TabHistoryMenu(history, open, { open = false }, onPick)
    }
}

@Composable
fun ForwardButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) = BarIconButton(
    Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.action_forward), enabled, onClick, modifier,
)

@Composable
private fun BarIconButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    IconButton(onClick = onClick, enabled = enabled, colors = barIconColors(), modifier = modifier.size(48.dp)) {
        Icon(icon, contentDescription = description)
    }
}

/** The Classic bar's Tabs button: a rounded square with the tab count in it. A long press asks for the quick actions. */
@Composable
fun TabsButton(count: Int, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = pluralStringResource(R.plurals.tabs_button_cd, count, count)
    LongPressIconButton(
        onClick = onClick,
        onLongClick = onLongClick,
        longClickLabel = stringResource(R.string.cd_long_press_tab_actions),
        modifier = modifier.semantics { contentDescription = description },
    ) {
        val color = MaterialTheme.colorScheme.onSurfaceVariant
        Box(Modifier.size(24.dp).border(2.dp, color, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text(
                tabCountGlyph(count),
                style = MaterialTheme.typography.labelMedium,
                // Fixed size: the digits sit in a 24 dp square and must not clip at large font scales.
                fontSize = 12.sp,
                color = color,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/** A 48 dp round button over the page, as the Compact bar draws its controls. */
@Composable
fun FloatingCircle(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 3.dp,
        modifier = modifier.size(48.dp),
    ) { Box(contentAlignment = Alignment.Center) { content() } }
}

/**
 * The More button and its menu: a new tab, a new private tab, all tabs (Compact only: Classic has the Tabs button),
 * switch the address-bar style, then Settings. In Compact a long press opens the quick actions (Classic has them on Tabs).
 */
@Composable
fun MoreMenu(style: AddressBarStyle, tabs: TabActions, onSwitchStyle: () -> Unit, onOpenSettings: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var quickOpen by remember { mutableStateOf(false) }
    val compact = style == AddressBarStyle.Compact
    Box(modifier) {
        LongPressIconButton(
            onClick = { open = true },
            onLongClick = if (compact) { { quickOpen = true } } else null,
            longClickLabel = if (compact) stringResource(R.string.cd_long_press_tab_actions) else null,
        ) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.action_more),
                // Compact draws horizontal dots, Classic vertical ones.
                modifier = if (style == AddressBarStyle.Compact) Modifier.rotate(90f) else Modifier,
            )
        }
        LumiDropdownMenu(expanded = open, onDismiss = { open = false }) {
            MenuItem(stringResource(R.string.menu_new_tab), { Icon(Icons.Filled.Add, null) }) {
                open = false
                tabs.newTab()
            }
            MenuItem(stringResource(R.string.menu_new_private_tab), { Icon(painterResource(R.drawable.ic_domino_mask), null) }) {
                open = false
                tabs.newPrivateTab()
            }
            if (compact) {
                MenuItem(stringResource(R.string.menu_all_tabs, tabs.count), { Icon(painterResource(R.drawable.ic_tab), null) }) {
                    open = false
                    tabs.openGrid()
                }
            }
            MenuItem(
                stringResource(if (compact) R.string.menu_use_classic_bar else R.string.menu_use_compact_bar),
                { Icon(painterResource(R.drawable.ic_swap_vert), null) },
            ) {
                open = false
                onSwitchStyle()
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            MenuItem(stringResource(R.string.menu_settings), { Icon(Icons.Filled.Settings, null) }) {
                open = false
                onOpenSettings()
            }
        }
        if (compact) QuickActionsMenu(quickOpen, { quickOpen = false }, tabs)
    }
}

/** Keeps a faded-out bar from taking touches or being read out by a screen reader. */
fun Modifier.inertWhen(inert: Boolean): Modifier = if (!inert) this else this
    .clearAndSetSemantics {}
    .pointerInput(Unit) {
        awaitPointerEventScope {
            while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
        }
    }
