package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.engine.LinkTarget

/** The long-pressed link: its title (when it has one) and full URL, then what to do with it. Every action closes the sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkSheet(link: LinkTarget, onAction: (LinkAction) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)
                .semantics { paneTitle = link.title ?: link.url },
        ) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                link.title?.let {
                    Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                }
                Text(link.url, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))
            linkActions(link.inPrivateTab).forEach { action ->
                Row(
                    Modifier.fillMaxWidth().height(56.dp).clickable { onAction(action) }.padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val icon = when (action) {
                        LinkAction.Open -> painterResource(R.drawable.ic_open_in_browser)
                        LinkAction.OpenInNewTab -> painterResource(R.drawable.ic_open_in_new)
                        LinkAction.OpenInPrivateTab, LinkAction.OpenInNewPrivateTab -> painterResource(R.drawable.ic_domino_mask)
                        LinkAction.Copy -> painterResource(R.drawable.ic_content_copy)
                        LinkAction.Share -> rememberVectorPainter(Icons.Filled.Share)
                    }
                    Icon(icon, null, Modifier.size(24.dp), MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(16.dp))
                    Text(stringResource(action.label), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

private val LinkAction.label: Int
    get() = when (this) {
        LinkAction.Open -> R.string.link_open
        LinkAction.OpenInNewTab -> R.string.link_open_new_tab
        LinkAction.OpenInPrivateTab -> R.string.link_open_private_tab
        LinkAction.OpenInNewPrivateTab -> R.string.link_open_new_private_tab
        LinkAction.Copy -> R.string.link_copy
        LinkAction.Share -> R.string.link_share
    }

