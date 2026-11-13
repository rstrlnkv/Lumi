package ru.digitalpunk.lumi.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.downloads.FileKind
import ru.digitalpunk.lumi.downloads.ellipsizeKeepingExtension
import ru.digitalpunk.lumi.downloads.fileKind
import ru.digitalpunk.lumi.downloads.sizeAndSite
import ru.digitalpunk.lumi.engine.DownloadOffer

private const val NAME_MAX_CHARS = 40

/** The drawable of a file-type tile (spec 3.1). */
fun FileKind.icon(): Int = when (this) {
    FileKind.Pdf -> R.drawable.ic_picture_as_pdf
    FileKind.Image -> R.drawable.ic_image
    FileKind.Video -> R.drawable.ic_video_file
    FileKind.Audio -> R.drawable.ic_audio_file
    FileKind.Archive -> R.drawable.ic_folder_zip
    FileKind.DiskImage -> R.drawable.ic_album
    FileKind.Apk -> R.drawable.ic_android
    FileKind.Other -> R.drawable.ic_draft
}

/** A circle with the file-type icon; [error] colours a failed entry. */
@Composable
fun FileTile(kind: FileKind, size: Dp, error: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.size(size).background(if (error) colors.errorContainer else colors.secondaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) { Icon(painterResource(kind.icon()), null, Modifier.size(24.dp), tint = if (error) colors.onErrorContainer else colors.onSecondaryContainer) }
}

/**
 * Asks before a file a page sent is saved (spec section 1). [name] is the sanitised name the file will get. Swipe, scrim and
 * Back cancel. Nothing about a private offer goes to the saved state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadAskSheet(offer: DownloadOffer, name: String, onDownload: () -> Unit, onCancel: () -> Unit) {
    val size = if (offer.contentLength >= 0) Formatter.formatShortFileSize(LocalContext.current, offer.contentLength) else null
    val site = offer.site
    val sizeText = size ?: stringResource(R.string.download_size_unknown)
    val resources = LocalResources.current
    val detail = sizeAndSite(sizeText, site) { a, b -> resources.getString(R.string.download_size_site, a, b) }
    val pane = if (site.isNullOrBlank()) stringResource(R.string.cd_download_ask_no_site, name, sizeText) else stringResource(R.string.cd_download_ask, name, sizeText, site)
    ModalBottomSheet(onDismissRequest = onCancel, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(start = 24.dp, end = 24.dp, bottom = 16.dp).semantics { paneTitle = pane }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FileTile(fileKind(offer.mimeType, name), 48.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(ellipsizeKeepingExtension(name, NAME_MAX_CHARS), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (offer.inPrivateTab) {
                Spacer(Modifier.height(16.dp))
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(12.dp)).padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_visibility), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.download_private_warning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).heightIn(min = 40.dp)) { Text(stringResource(R.string.action_cancel)) }
                Button(onClick = onDownload, modifier = Modifier.weight(1f).heightIn(min = 40.dp)) { Text(stringResource(R.string.download_action)) }
            }
        }
    }
}

/** The 20 dp private badge on a tile. */
@Composable
fun PrivateBadge() {
    Box(Modifier.size(20.dp).background(MaterialTheme.colorScheme.inverseSurface, CircleShape), contentAlignment = Alignment.Center) {
        Icon(painterResource(R.drawable.ic_domino_mask), null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.inverseOnSurface)
    }
}

