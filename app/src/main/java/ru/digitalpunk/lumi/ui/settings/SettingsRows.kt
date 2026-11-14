package ru.digitalpunk.lumi.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.R

/** A Settings page: a large top app bar with Back, then the content in a scrolling column with 16 dp margins. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageScaffold(title: String, onBack: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val background = MaterialTheme.colorScheme.surfaceContainer
    Scaffold(
        modifier = modifier,
        containerColor = background,
        topBar = {
            LargeTopAppBar(
                title = { Text(title, modifier = Modifier.semantics { heading() }) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = background, scrolledContainerColor = background),
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = { content(); Spacer(Modifier.height(8.dp)) },
        )
    }
}

/** Rows with 2 dp gaps; the group rounds its outer corners to 20 dp, each row its own to 4 dp. */
@Composable
fun SettingsGroup(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)), verticalArrangement = Arrangement.spacedBy(2.dp), content = content)
}

/** One row of a group: an optional leading icon, a headline, an optional supporting line and an optional trailing part. */
@Composable
fun SettingsRow(
    headline: String,
    supporting: String?,
    icon: Painter?,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    SettingsRowFrame(headline, supporting, modifier, trailing, leading = icon?.let { { Icon(it, contentDescription = null) } })
}

/** A top-level category: its icon sits in a 40 dp circle of [container] / [onContainer]. */
@Composable
fun CategoryRow(headline: String, supporting: String, icon: Painter, container: Color, onContainer: Color, modifier: Modifier = Modifier) {
    SettingsRowFrame(headline, supporting, modifier, trailing = null, leading = {
        Box(Modifier.size(40.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = onContainer)
        }
    })
}

@Composable
private fun SettingsRowFrame(
    headline: String,
    supporting: String?,
    modifier: Modifier,
    trailing: (@Composable () -> Unit)?,
    leading: (@Composable () -> Unit)?,
) {
    ListItem(
        headlineContent = { Text(headline) },
        supportingContent = supporting?.let { { Text(it) } },
        leadingContent = leading,
        trailingContent = trailing,
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        modifier = Modifier.clip(RoundedCornerShape(4.dp)).then(modifier),
    )
}

/** A row whose value is a choice of two or three: the headline, then [ChoiceButtons] in the same cell. */
@Composable
fun SettingsChoiceRow(headline: String, icon: Painter, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceBright)) {
        ListItem(
            headlineContent = { Text(headline) },
            leadingContent = { Icon(icon, contentDescription = null) },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )
        ChoiceButtons(options, selected, onSelect, Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp))
    }
}

/**
 * A connected group of two or three buttons, one selected: the selected one is `primary` with a check and full corners,
 * the others `surfaceContainerHighest` with 8 dp inner corners. Material 3 1.4.0 has no ButtonGroup, so it is drawn here.
 */
@Composable
fun ChoiceButtons(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { index, label ->
            val on = index == selected
            val outer = 20.dp
            val inner = 8.dp
            val shape = when {
                on -> CircleShape
                index == 0 -> RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = inner, bottomEnd = inner)
                index == options.lastIndex -> RoundedCornerShape(topStart = inner, bottomStart = inner, topEnd = outer, bottomEnd = outer)
                else -> RoundedCornerShape(inner)
            }
            val colors = MaterialTheme.colorScheme
            val content = if (on) colors.onPrimary else colors.onSurface
            Row(
                Modifier.weight(1f).height(40.dp).clip(shape).background(if (on) colors.primary else colors.surfaceContainerHighest)
                    .selectable(selected = on, role = Role.RadioButton) { onSelect(index) }.padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (on) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(label, color = content, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
