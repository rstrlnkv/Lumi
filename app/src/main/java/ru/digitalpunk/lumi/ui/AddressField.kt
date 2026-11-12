package ru.digitalpunk.lumi.ui

import android.view.inputmethod.EditorInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.PlatformTextInputMethodRequest
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.BuildConfig
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.address.SiteStatus
import ru.digitalpunk.lumi.address.displayUrl
import ru.digitalpunk.lumi.address.editableUrl
import ru.digitalpunk.lumi.address.resolveInput
import ru.digitalpunk.lumi.data.AddressBarStyle
import ru.digitalpunk.lumi.engine.PageState
import ru.digitalpunk.lumi.privacy.LocalPrivateTab

/** The address pill both bar styles share. Sizes and colours follow the design for [style]. */
@Composable
fun AddressField(
    state: PageState,
    site: SiteStatus,
    style: AddressBarStyle,
    editing: Boolean,
    onEditingChange: (Boolean) -> Unit,
    onSubmit: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    onShowProtection: () -> Unit,
    modifier: Modifier = Modifier,
    tabs: TabActions? = null,
) {
    val swipe = remember { BarSwipeState() }
    val scope = rememberCoroutineScope()
    val swipeEnabled = tabs != null && !editing
    LaunchedEffect(swipeEnabled) { if (!swipeEnabled) swipe.reset() }
    val compact = style == AddressBarStyle.Compact
    val focusManager = LocalFocusManager.current
    val textStyle = MaterialTheme.typography.bodyLarge
    val startPadding = if (compact) 14.dp else 16.dp
    val lockGap = if (compact) 6.dp else 8.dp

    Surface(
        shape = CircleShape,
        color = if (compact) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceContainerHighest,
        shadowElevation = if (compact) 3.dp else 0.dp,
        modifier = modifier.fillMaxWidth().height(48.dp).then(if (tabs != null && swipeEnabled) Modifier.barSwipe(swipe, tabs, scope) else Modifier),
    ) {
        Box {
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    val text = rememberTextFieldState()
                    val focusRequester = remember { FocusRequester() }
                    var hadFocus by remember { mutableStateOf(false) }
                    // Keyed on the tab so a tab that opens while editing starts empty, not with the old URL.
                    LaunchedEffect(tabs?.selectedId) {
                        text.setTextAndSelectAll(editableUrl(state.url))
                        focusRequester.requestFocus()
                    }
                    BackHandler { focusManager.clearFocus() }
                    val hint = stringResource(R.string.address_hint)
                    NoLearningInPrivateTab {
                        BasicTextField(
                            state = text,
                            textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            lineLimits = TextFieldLineLimits.SingleLine,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, keyboardType = KeyboardType.Uri),
                            onKeyboardAction = {
                                resolveInput(text.text.toString(), debugPages = BuildConfig.DEBUG)?.let(onSubmit)
                                focusManager.clearFocus()
                            },
                            decorator = { inner ->
                                Box(Modifier.heightIn(min = 48.dp), contentAlignment = Alignment.CenterStart) {
                                    if (text.text.isEmpty()) {
                                        Text(hint, style = textStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    inner()
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = startPadding)
                                .semantics { contentDescription = hint }
                                .focusRequester(focusRequester)
                                .onFocusChanged {
                                    if (it.isFocused) hadFocus = true else if (hadFocus) onEditingChange(false)
                                },
                        )
                    }
                } else {
                    val display = displayUrl(state.url)
                    val switchActions = listOfNotNull(
                        tabs?.prevHost?.let { it to -1 },
                        tabs?.nextHost?.let { it to 1 },
                    ).map { (host, by) ->
                        CustomAccessibilityAction(stringResource(R.string.action_switch_to_tab, host)) {
                            tabs?.switchBy?.invoke(by)
                            true
                        }
                    }
                    Box(Modifier.weight(1f).clipToBounds().onSizeChanged { swipe.width = it.width.toFloat() }) {
                        Row(
                            Modifier.fillMaxWidth()
                                .semantics { customActions = switchActions }
                                .graphicsLayer { translationX = swipe.offset }
                                .clickable(onClickLabel = stringResource(R.string.action_edit_address), role = Role.Button) {
                                    onEditingChange(true)
                                }
                                .heightIn(min = 48.dp)
                                // The shield's 8 dp tap padding sits inside its target, so it keeps the same place.
                                .padding(start = if (site == SiteStatus.None) startPadding else startPadding - 8.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SiteStatusButton(site, gap = lockGap, onClick = onShowProtection)
                            val empty = display.text.isEmpty()
                            Text(
                                text = if (empty) stringResource(R.string.address_hint) else display.text,
                                style = textStyle,
                                color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        // The neighbour's host comes in from the edge the finger moves towards.
                        val peek = if (swipe.settling) null else if (swipe.offset < 0f) tabs?.nextHost else if (swipe.offset > 0f) tabs?.prevHost else null
                        if (peek != null) {
                            Box(
                                Modifier.matchParentSize().graphicsLayer {
                                    translationX = swipe.offset + (if (swipe.offset < 0f) size.width else -size.width)
                                },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    peek,
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.clearAndSetSemantics {},
                                )
                            }
                        }
                    }
                }
                val trailing = if (compact) Modifier.size(48.dp) else Modifier.padding(end = 4.dp).size(48.dp)
                if (state.isLoading) {
                    IconButton(onClick = onStop, modifier = trailing) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_stop))
                    }
                } else {
                    IconButton(onClick = onReload, modifier = trailing) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_reload))
                    }
                }
            }
            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    trackColor = MaterialTheme.colorScheme.secondaryContainer,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(start = if (compact) 24.dp else 28.dp, end = if (compact) 24.dp else 28.dp, bottom = 1.dp)
                        .height(3.dp),
                )
            }
        }
    }
}

/** In a private tab, asks the keyboard not to learn from what is typed (`IME_FLAG_NO_PERSONALIZED_LEARNING`). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun NoLearningInPrivateTab(content: @Composable () -> Unit) {
    val isPrivate by rememberUpdatedState(LocalPrivateTab.current)
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            next.startInputMethod(
                PlatformTextInputMethodRequest { info ->
                    request.createInputConnection(info).also {
                        if (isPrivate) info.imeOptions = info.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
                    }
                },
            )
        },
        content = content,
    )
}
