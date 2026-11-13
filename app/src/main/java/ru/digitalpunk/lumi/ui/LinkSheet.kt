package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.engine.ContextTarget

/**
 * The long-pressed link or image: a title (when there is one) and the full URL, then what to do with it. An image inside a
 * link shows the link's header and items, a divider, then the image items. Every action closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSheet(target: ContextTarget, onAction: (LinkAction) -> Unit, onDismiss: () -> Unit) {
    val link = target.link
    val image = target.image
    val title = link?.title ?: if (link == null) image?.title else null
    val url = link?.url ?: image?.url.orEmpty()
    val pane = if (link == null && image != null) stringResource(R.string.cd_image_sheet, image.title ?: image.url) else title ?: url
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)
                .semantics { paneTitle = pane },
        ) {
            Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                if (image != null) {
                    // A tile, not the picture: drawing it would fetch the image a second time.
                    Box(
                        Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.tertiaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_image), null, Modifier.size(24.dp), MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                    Spacer(Modifier.size(12.dp))
                }
                Column {
                    title?.let {
                        Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                    }
                    Text(url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            sheetGroups(link != null, image != null, target.inPrivateTab).forEachIndexed { index, group ->
                if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                group.forEach { action -> ActionRow(action, linkedImage = link != null && image != null) { onAction(action) } }
            }
        }
    }
}

@Composable
private fun ActionRow(action: LinkAction, linkedImage: Boolean, onClick: () -> Unit) {
    Row(
        // A minimum, not a fixed height: a long label at a large font scale wraps instead of clipping.
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val icon = when (action) {
            LinkAction.Open -> painterResource(R.drawable.ic_open_in_browser)
            LinkAction.OpenInNewTab -> painterResource(R.drawable.ic_open_in_new)
            LinkAction.OpenInPrivateTab, LinkAction.OpenInNewPrivateTab, LinkAction.OpenImageInNewPrivateTab -> painterResource(R.drawable.ic_domino_mask)
            LinkAction.Copy -> painterResource(R.drawable.ic_content_copy)
            LinkAction.Share, LinkAction.ShareImage -> rememberVectorPainter(Icons.Filled.Share)
            LinkAction.SaveImage -> painterResource(R.drawable.ic_download)
            LinkAction.OpenImageInNewTab -> painterResource(R.drawable.ic_image)
            LinkAction.CopyImageAddress -> painterResource(R.drawable.ic_link)
        }
        Icon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(16.dp))
        Text(stringResource(action.label(linkedImage)), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** On a linked image, Share says "Share link" so it is not taken for the image. */
private fun LinkAction.label(linkedImage: Boolean): Int = when (this) {
    LinkAction.Open -> R.string.link_open
    LinkAction.OpenInNewTab -> R.string.link_open_new_tab
    LinkAction.OpenInPrivateTab -> R.string.link_open_private_tab
    LinkAction.OpenInNewPrivateTab -> R.string.link_open_new_private_tab
    LinkAction.Copy -> R.string.link_copy
    LinkAction.Share -> if (linkedImage) R.string.link_share_link else R.string.link_share
    LinkAction.SaveImage -> R.string.image_save
    LinkAction.OpenImageInNewTab -> R.string.image_open_in_new_tab
    LinkAction.OpenImageInNewPrivateTab -> R.string.image_open_in_new_private_tab
    LinkAction.CopyImageAddress -> R.string.image_copy_address
    LinkAction.ShareImage -> R.string.image_share
}
