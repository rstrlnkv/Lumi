package ru.digitalpunk.lumi.ui.about

import android.text.format.DateUtils
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import ru.digitalpunk.lumi.LumiApplication
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.about.Channel
import ru.digitalpunk.lumi.about.installedChannel
import ru.digitalpunk.lumi.ui.settings.labelRes
import ru.digitalpunk.lumi.ui.settings.summaryRes
import ru.digitalpunk.lumi.updates.CheckResult
import ru.digitalpunk.lumi.updates.askUpdateNotifications

/** About's update block (about-screen standard rule 4): the channel, the last successful check and "Check now". */
@Composable
internal fun UpdateBlock(onOpenUrl: (String) -> Unit) {
    val context = LocalContext.current
    val activity = LocalActivity.current
    val app = context.applicationContext as LumiApplication
    val scope = rememberCoroutineScope()
    val stored by app.settings.updateChannel.collectAsStateWithLifecycle<String?>(null)
    val lastCheck by app.settings.lastUpdateCheck.collectAsStateWithLifecycle<Long?>(null)
    var checking by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CheckResult?>(null) }
    val channel = stored?.let(Channel::of) ?: installedChannel
    ListItem(
        overlineContent = { Text(stringResource(R.string.settings_update_channel)) },
        headlineContent = { Text(stringResource(channel.labelRes())) },
        supportingContent = { Text(stringResource(channel.summaryRes())) },
        leadingContent = { Icon(Icons.Filled.Build, contentDescription = null) },
    )
    ListItem(
        headlineContent = { Text(stringResource(R.string.about_last_checked)) },
        supportingContent = {
            Text(lastCheck?.let { DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH) }
                ?: stringResource(R.string.about_last_checked_never))
        },
        leadingContent = { Icon(painterResource(R.drawable.ic_refresh), contentDescription = null) },
        trailingContent = {
            TextButton(enabled = !checking, onClick = {
                activity?.let(::askUpdateNotifications)
                checking = true
                scope.launch {
                    result = app.updates.check()
                    checking = false
                }
            }) { Text(stringResource(R.string.about_check_now)) }
        },
    )
    when (val r = result) {
        is CheckResult.Available -> ListItem(
            headlineContent = { Text(stringResource(R.string.about_update_available, r.release.tag.display)) },
            modifier = Modifier.clickable(role = Role.Button) { onOpenUrl(r.release.url) },
        )
        CheckResult.UpToDate -> ListItem(headlineContent = { Text(stringResource(R.string.about_up_to_date)) })
        CheckResult.Failed -> ListItem(headlineContent = { Text(stringResource(R.string.about_check_failed)) })
        null -> Unit
    }
}
