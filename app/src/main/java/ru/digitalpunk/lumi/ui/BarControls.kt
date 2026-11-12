package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.data.AddressBarStyle

@Composable
private fun barIconColors() = IconButtonDefaults.iconButtonColors(
    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
)

@Composable
fun BackButton(enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) = BarIconButton(
    Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back), enabled, onClick, modifier,
)

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

/** The More button and its menu: a new tab, and switch the address-bar style. */
@Composable
fun MoreMenu(style: AddressBarStyle, tabs: TabActions, onSwitchStyle: () -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, colors = barIconColors(), modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.MoreVert,
                contentDescription = stringResource(R.string.action_more),
                // Compact draws horizontal dots, Classic vertical ones.
                modifier = if (style == AddressBarStyle.Compact) Modifier.rotate(90f) else Modifier,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_new_tab)) },
                onClick = {
                    open = false
                    tabs.newTab()
                },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (style == AddressBarStyle.Compact) R.string.menu_use_classic_bar else R.string.menu_use_compact_bar,
                        ),
                    )
                },
                onClick = {
                    open = false
                    onSwitchStyle()
                },
            )
        }
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
