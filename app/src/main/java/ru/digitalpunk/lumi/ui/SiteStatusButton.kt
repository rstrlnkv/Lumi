package ru.digitalpunk.lumi.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.address.SiteStatus
import ru.digitalpunk.lumi.address.countLabel

/** The icon for [site]; null for [SiteStatus.None], which shows nothing. */
@DrawableRes
fun siteIcon(site: SiteStatus): Int? = when (site) {
    SiteStatus.None -> null
    is SiteStatus.Protected -> R.drawable.ic_shield
    SiteStatus.ProtectionOff -> R.drawable.ic_remove_moderator
    SiteStatus.Insecure -> R.drawable.ic_no_encryption
}

/** What TalkBack reads for [site]: on https the protection, then "Secure connection"; on http only that the connection is not secure. */
@Composable
fun siteDescription(site: SiteStatus): String {
    val secure = ". " + stringResource(R.string.cd_secure_connection)
    return when (site) {
        SiteStatus.None -> ""
        is SiteStatus.Protected ->
            if (site.count > 0) pluralStringResource(R.plurals.cd_site_protection_blocked, site.count, site.count) + secure
            else stringResource(R.string.cd_site_protection_none) + secure
        SiteStatus.ProtectionOff -> stringResource(R.string.cd_site_protection_off) + secure
        SiteStatus.Insecure -> stringResource(R.string.cd_site_insecure)
    }
}

/** Shield and blocked count at the start of the address; a tap opens the protection sheet. [gap] is the space before the domain, inside the target. */
@Composable
fun SiteStatusButton(site: SiteStatus, gap: Dp, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val icon = siteIcon(site) ?: return
    val description = siteDescription(site)
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .heightIn(min = 48.dp)
            .widthIn(min = 48.dp)
            .clickable(onClickLabel = stringResource(R.string.action_show_protection), role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(start = 8.dp, end = gap),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        val count = (site as? SiteStatus.Protected)?.let { countLabel(it.count) }.orEmpty()
        if (count.isNotEmpty()) {
            Spacer(Modifier.width(2.dp))
            Text(
                count,
                style = MaterialTheme.typography.labelMedium,
                // Fixed size, as in the Tabs button: the count must not push the domain out of the pill at large font scales.
                fontSize = 12.sp,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.clearAndSetSemantics {},
            )
        }
    }
}
