package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.digitalpunk.lumi.data.icons.SiteIcon
import ru.digitalpunk.lumi.data.icons.SiteIcons
import ru.digitalpunk.lumi.data.icons.TileMode
import ru.digitalpunk.lumi.data.icons.iconHost
import ru.digitalpunk.lumi.data.icons.letterColours
import ru.digitalpunk.lumi.data.icons.tileColour
import ru.digitalpunk.lumi.data.icons.tileMode
import ru.digitalpunk.lumi.ui.library.ColouredLetterTile
import ru.digitalpunk.lumi.ui.library.LetterTile

/** The process's site icons; null in previews. */
val LocalSiteIcons = staticCompositionLocalOf<SiteIcons?> { null }

/** The stored icon of [url]'s host, redrawn when an icon is written. [backfill] asks for a missing or stale one (never from a private tab). */
@Composable
fun rememberSiteIcon(url: String, backfill: Boolean): SiteIcon? {
    val icons = LocalSiteIcons.current
    val host = remember(url) { iconHost(url) }
    if (icons == null || host == null) return null
    val changes by icons.changes.collectAsStateWithLifecycle()
    if (backfill) LaunchedEffect(host) { icons.request(url, retryFailed = false) }
    return produceState(icons.peek(host), host, changes) { value = icons.load(host) }.value
}

/**
 * A site's icon in a [size] tile, by [tileMode]: a sharp opaque icon fills it (with a hairline when its edge is light), a
 * transparent one sits centred on a neutral tile. With [smallAsLetter] (the start page) a small icon or none gives a letter
 * tile in the site's colour, like Safari; otherwise no icon shows the first letter of [title] on a neutral tile.
 */
@Composable
fun SiteIconTile(
    url: String, title: String, size: Dp, corner: Dp, backfill: Boolean,
    letterStyle: TextStyle = MaterialTheme.typography.titleMedium, smallAsLetter: Boolean = false,
) {
    val icon = rememberSiteIcon(url, backfill)
    val shape = RoundedCornerShape(corner)
    if (icon == null) {
        val host = remember(url) { iconHost(url) }
        return if (smallAsLetter && host != null) ColouredLetterTile(title, remember(host) { letterColours(tileColour(null, null, host)) }, size, corner, letterStyle)
        else LetterTile(title, size, corner, letterStyle)
    }
    val image = remember(icon) { icon.bitmap.asImageBitmap() }
    when (tileMode(icon.iconClass, smallAsLetter, icon.fillsTile)) {
        TileMode.Image -> {
            val edge = if (icon.lightEdge) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape) else Modifier
            Image(image, null, Modifier.size(size).clip(shape).then(edge), contentScale = ContentScale.Crop)
        }
        // About 36 dp in the 64 dp start-page tile, the same share of a smaller one.
        TileMode.SmallIcon -> Box(Modifier.size(size).background(MaterialTheme.colorScheme.surfaceContainerHighest, shape), contentAlignment = Alignment.Center) {
            Image(image, null, Modifier.size(size * 4 / 7))
        }
        TileMode.Letter -> ColouredLetterTile(title, icon.letter, size, corner, letterStyle)
    }
}
