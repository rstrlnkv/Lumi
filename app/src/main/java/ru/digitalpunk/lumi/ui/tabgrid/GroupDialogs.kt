package ru.digitalpunk.lumi.ui.tabgrid

import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import ru.digitalpunk.lumi.R
import ru.digitalpunk.lumi.engine.MAX_GROUP_NAME_LENGTH
import ru.digitalpunk.lumi.engine.normalizeGroupName
import ru.digitalpunk.lumi.engine.sanitizeGroupInput

/**
 * Asks for a group name. [initial] is null when creating; renaming starts with the whole name selected.
 * [otherNames] leaves out the group's own name; [reserved] is the shared list's name.
 */
@Composable
fun GroupNameDialog(
    initial: String?,
    otherNames: List<String>,
    reserved: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var value by remember { mutableStateOf(TextFieldValue(initial.orEmpty(), TextRange(0, initial.orEmpty().length))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val problem = groupNameProblem(value.text, otherNames, reserved)
    val confirm = {
        if (problem == null) {
            val name = normalizeGroupName(value.text)
            if (name == initial) onDismiss() else onConfirm(name)
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.group_create_title else R.string.group_rename_title)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.copy(text = sanitizeGroupInput(it.text)) },
                label = { Text(stringResource(R.string.group_name_label)) },
                singleLine = true,
                isError = problem == NameProblem.Duplicate,
                supportingText = {
                    if (problem == NameProblem.Duplicate) {
                        Text(stringResource(R.string.group_name_error_duplicate))
                    } else {
                        Text(stringResource(R.string.group_name_counter, value.text.codePointCount(0, value.text.length), MAX_GROUP_NAME_LENGTH))
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { confirm() }),
                modifier = Modifier.focusRequester(focus),
            )
        },
        confirmButton = {
            TextButton(onClick = { confirm() }, enabled = problem == null) {
                Text(stringResource(if (initial == null) R.string.action_create else R.string.action_rename))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** Confirms deleting the group [name] together with its [tabCount] tabs. */
@Composable
fun DeleteGroupDialog(name: String, tabCount: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(pluralStringResource(R.plurals.group_delete_title, tabCount, tabCount)) },
        text = { Text(stringResource(R.string.group_delete_body, name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
