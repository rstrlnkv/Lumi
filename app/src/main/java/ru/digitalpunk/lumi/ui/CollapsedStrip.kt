package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.address.SiteStatus
import ru.digitalpunk.lumi.address.displayUrl
import ru.digitalpunk.lumi.privacy.LocalPrivateTab

/** Height of the visible strip or pill. */
private val StripHeight: Dp = 32.dp

/** Height of what is left of the bar when collapsed (above the nav inset): the 48 dp tap layer around the strip. */
val CollapsedStripHeight: Dp = 48.dp

/** The two bar layers cross-fade without overlap: controls fade out over the first half of the slide, the strip in over the second. */
fun controlsAlpha(progress: Float) = (1f - 2f * progress).coerceIn(0f, 1f)

fun stripAlpha(progress: Float) = (2f * progress - 1f).coerceIn(0f, 1f)

/** An expanded or sliding band is opaque and takes the touches; a fully collapsed one lets them through to the page. */
fun bandTakesTouches(collapsed: Boolean) = !collapsed

enum class FieldTap { Expand, Set }

/** A request to start editing while the bar is not fully expanded only expands it; leaving edit mode always passes. */
fun fieldTap(wantEditing: Boolean, barExpanded: Boolean): FieldTap =
    if (wantEditing && !barExpanded) FieldTap.Expand else FieldTap.Set

/** Site status icon (no count) and domain of the page; a tap shows the whole bar again once it is fully collapsed. */
@Composable
fun CollapsedStrip(
    url: String,
    site: SiteStatus,
    tappable: Boolean,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    visual: Modifier = Modifier,
) {
    val display = displayUrl(url)
    val description = stringResource(R.string.action_expand_bar)
    Box(
        modifier
            .height(CollapsedStripHeight)
            .then(
                if (tappable) Modifier.clickable(onClickLabel = description, role = Role.Button, onClick = onExpand)
                else Modifier.clearAndSetSemantics {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            visual.height(StripHeight),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            siteIcon(site, LocalPrivateTab.current)?.let { icon ->
                Icon(
                    painterResource(icon),
                    // The strip is one "expand" target: the shield's long phrase is read on the expanded bar's button.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                display.text.ifEmpty { stringResource(R.string.address_hint) },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
