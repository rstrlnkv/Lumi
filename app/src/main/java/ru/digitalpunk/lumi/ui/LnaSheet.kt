package ru.digitalpunk.lumi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.engine.LnaAnswer
import ru.digitalpunk.lumi.engine.LnaKind
import ru.digitalpunk.lumi.engine.LnaPrompt

/**
 * Chrome's local network prompt (ADR 015), as a card above the bottom bar: the page stays visible and the bar stays
 * reachable. Back dismisses it unanswered, so the site can ask again. Allow ignores taps for [ALLOW_DELAY_MILLIS] after the
 * card appears, so a page cannot time its request under a tap meant for the page; the text scrolls and the buttons stay.
 */
@Composable
fun LnaSheet(prompt: LnaPrompt, modifier: Modifier, onAnswer: (LnaAnswer) -> Unit) {
    BackHandler { onAnswer(LnaAnswer.Dismiss) }
    val pane = stringResource(R.string.cd_lna_sheet)
    var armed by remember(prompt.key) { mutableStateOf(false) }
    LaunchedEffect(prompt.key) {
        delay(ALLOW_DELAY_MILLIS)
        armed = true
    }
    Surface(
        modifier.statusBarsPadding().fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp).semantics { paneTitle = pane },
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 16.dp)) {
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(
                    stringResource(R.string.lna_title, prompt.site),
                    Modifier.semantics { heading() },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (prompt.key.kind == LnaKind.Network) R.string.lna_network else R.string.lna_device),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                prompt.via?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.lna_via, it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onAnswer(LnaAnswer.Block) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.lna_block)) }
                Button(onClick = { if (armed) onAnswer(LnaAnswer.Allow) }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(stringResource(R.string.lna_allow)) }
            }
        }
    }
}

private const val ALLOW_DELAY_MILLIS = 400L
