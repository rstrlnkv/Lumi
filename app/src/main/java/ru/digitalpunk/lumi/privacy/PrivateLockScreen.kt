package ru.digitalpunk.lumi.privacy

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R

/** The locked private tabs: no title, only how many there are and the Unlock button. */
@Composable
fun PrivateLockColumn(count: Int, onUnlock: () -> Unit, modifier: Modifier = Modifier) = PrivateMessage(
    icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp)) },
    title = stringResource(R.string.private_locked_title),
    body = pluralStringResource(R.plurals.private_locked_body, count, count),
    action = stringResource(R.string.private_unlock),
    onAction = onUnlock,
    modifier = modifier,
)

/** The Private chip with no tabs. */
@Composable
fun PrivateEmptyColumn(onNewPrivateTab: () -> Unit, modifier: Modifier = Modifier) = PrivateMessage(
    icon = {
        Icon(painterResource(R.drawable.ic_domino_mask), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
    },
    title = stringResource(R.string.private_empty_title),
    body = stringResource(R.string.private_empty_body),
    action = stringResource(R.string.action_new_private_tab),
    onAction = onNewPrivateTab,
    modifier = modifier,
)

/** Covers the page while a private tab waits for unlock; nothing under it can be touched. */
@Composable
fun PrivateLockScreen(count: Int, onUnlock: () -> Unit, modifier: Modifier = Modifier) =
    PrivateLockColumn(count, onUnlock, modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).pointerInput(Unit) {})

@Composable
private fun PrivateMessage(icon: @Composable () -> Unit, title: String, body: String, action: String, onAction: () -> Unit, modifier: Modifier) {
    // Scrolls when it does not fit (landscape, large font), and stays centred when it does.
    BoxWithConstraints(modifier.fillMaxSize()) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).fillMaxWidth().heightIn(min = maxHeight).padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            icon()
            Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = onAction) { Text(action) }
        }
    }
}
