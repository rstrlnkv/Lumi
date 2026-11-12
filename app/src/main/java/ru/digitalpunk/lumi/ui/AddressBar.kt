package ru.digitalpunk.lumi.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.digitalpunk.lumi.BuildConfig
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.address.displayUrl
import ru.digitalpunk.lumi.address.editableUrl
import ru.digitalpunk.lumi.address.resolveInput
import ru.digitalpunk.lumi.engine.PageState

@Composable
fun AddressBar(
    state: PageState,
    onSubmit: (String) -> Unit,
    onReload: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val textStyle = MaterialTheme.typography.bodyLarge

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Box {
            Row(Modifier.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (editing) {
                    val text = rememberTextFieldState()
                    val focusRequester = remember { FocusRequester() }
                    var hadFocus by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        text.setTextAndSelectAll(editableUrl(state.url))
                        focusRequester.requestFocus()
                    }
                    BackHandler { focusManager.clearFocus() }
                    val hint = stringResource(R.string.address_hint)
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
                            .padding(start = 16.dp)
                            .semantics { contentDescription = hint }
                            .focusRequester(focusRequester)
                            .onFocusChanged {
                                if (it.isFocused) hadFocus = true else if (hadFocus) editing = false
                            },
                    )
                } else {
                    val display = displayUrl(state.url)
                    Row(
                        Modifier.weight(1f)
                            .clickable(onClickLabel = stringResource(R.string.action_edit_address), role = Role.Button) {
                                editing = true
                            }
                            .heightIn(min = 48.dp)
                            .padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (display.isSecure) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = stringResource(R.string.cd_secure_connection),
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        val empty = display.text.isEmpty()
                        Text(
                            text = if (empty) stringResource(R.string.address_hint) else display.text,
                            style = textStyle,
                            color = if (empty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = if (display.isSecure) 6.dp else 0.dp),
                        )
                    }
                }
                if (state.isLoading) {
                    IconButton(onClick = onStop) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_stop))
                    }
                } else {
                    IconButton(onClick = onReload) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_reload))
                    }
                }
            }
            if (state.isLoading) {
                LinearProgressIndicator(
                    progress = { state.progress / 100f },
                    trackColor = Color.Transparent,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp),
                )
            }
        }
    }
}
