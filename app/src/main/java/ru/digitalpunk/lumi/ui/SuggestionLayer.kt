package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.address.searchUrl
import ru.digitalpunk.lumi.data.search.EngineSuggestClient
import ru.digitalpunk.lumi.data.search.SearchEngine
import ru.digitalpunk.lumi.suggest.LocalIndex
import ru.digitalpunk.lumi.suggest.SuggestQuery
import ru.digitalpunk.lumi.suggest.Suggestion
import ru.digitalpunk.lumi.suggest.SuggestionPipeline
import ru.digitalpunk.lumi.suggest.hostOf
import ru.digitalpunk.lumi.suggest.rankLocal
import ru.digitalpunk.lumi.suggest.typedPart

/**
 * The suggestion list while the address bar is edited: a scrim over the page and a sheet that reaches down behind the
 * bar ([barHeight]), best match nearest the bar. [engine] is null when suggestions are off or the setting is not read yet.
 */
@Composable
fun SuggestionLayer(
    editor: AddressEditor,
    engine: SearchEngine?,
    client: EngineSuggestClient,
    barHeight: Dp,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusManager = LocalFocusManager.current
    val language = LocalResources.current.configuration.locales[0].toLanguageTag()
    val index = editor.index
    val rows by remember(editor, index, engine, language) {
        SuggestionPipeline(
            local = { typed, pin -> rankLocal(index ?: LocalIndex.Empty, typed, pin) },
            remote = { e, q -> client.suggest(e, q, language) },
        ).run(snapshotFlow { SuggestQuery(typedPart(editor.text.text.toString(), editor.text.selection), engine, editor.completedHost) })
    }.collectAsStateWithLifecycle(emptyList())
    val typed = typedPart(editor.text.text.toString(), editor.text.selection).trim()
    val open = { url: String ->
        focusManager.clearFocus()
        onOpen(url)
    }

    Box(modifier.fillMaxSize()) {
        Box(
            Modifier.fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
        )
        if (rows.isNotEmpty()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                modifier = Modifier.align(Alignment.BottomCenter).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)).fillMaxWidth(),
            ) {
                Column {
                    LazyColumn(Modifier.weight(1f, fill = false).padding(top = 8.dp), reverseLayout = true) {
                        items(rows, key = { it.toString() }) { row ->
                            SuggestionRow(row, typed, onOpen = open, onSearch = { open(searchUrl(editor.searchPrefix, it)) }, onFill = editor.text::setTextAndPlaceCursorAtEnd)
                        }
                    }
                    Spacer(Modifier.height(barHeight))
                }
            }
        }
    }
}

@Composable
private fun SuggestionRow(row: Suggestion, typed: String, onOpen: (String) -> Unit, onSearch: (String) -> Unit, onFill: (String) -> Unit) {
    val colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    when (row) {
        is Suggestion.Search -> Row(verticalAlignment = Alignment.CenterVertically) {
            val description = stringResource(R.string.cd_suggestion_search, row.text)
            ListItem(
                headlineContent = { Text(searchHeadline(row.text, typed), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = { Icon(Icons.Filled.Search, contentDescription = null) },
                colors = colors,
                modifier = Modifier.weight(1f).clickable(role = Role.Button) { onSearch(row.text) }.semantics { contentDescription = description },
            )
            IconButton(onClick = { onFill(row.text) }, modifier = Modifier.padding(end = 4.dp)) {
                Icon(painterResource(R.drawable.ic_north_west), contentDescription = stringResource(R.string.cd_fill_suggestion, row.text))
            }
        }
        is Suggestion.History -> PageRow(row.title, row.url, R.drawable.ic_history, R.string.cd_suggestion_history, colors, onOpen)
        is Suggestion.Bookmark -> PageRow(row.title, row.url, R.drawable.ic_bookmark, R.string.cd_suggestion_bookmark, colors, onOpen)
    }
}

@Composable
private fun PageRow(title: String, url: String, icon: Int, description: Int, colors: androidx.compose.material3.ListItemColors, onOpen: (String) -> Unit) {
    val name = title.ifBlank { hostOf(url).ifEmpty { url } }
    val spoken = stringResource(description, name)
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(url.substringAfter("://"), maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingContent = { Icon(painterResource(icon), contentDescription = null) },
        colors = colors,
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(url) }.semantics { contentDescription = spoken },
    )
}

/** The typed part regular and the rest medium, when the query starts with what was typed. */
private fun searchHeadline(text: String, typed: String) = buildAnnotatedString {
    if (typed.isNotEmpty() && text.startsWith(typed, ignoreCase = true)) {
        append(text.substring(0, typed.length))
        withStyle(SpanStyle(fontWeight = FontWeight.Medium)) { append(text.substring(typed.length)) }
    } else {
        append(text)
    }
}
