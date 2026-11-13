package ru.digitalpunk.lumi.ui

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import ru.digitalpunk.lumi.address.DEFAULT_SEARCH_URL
import ru.digitalpunk.lumi.suggest.LocalIndex
import ru.digitalpunk.lumi.suggest.inlineCompletion

/** The address field's text, hoisted so the suggestion list reads it, and the inline completion applied to each edit. */
@Stable
class AddressEditor {
    val text = TextFieldState()

    /** Bookmarks and history for this edit; null while loading or when not editing. */
    var index by mutableStateOf<LocalIndex?>(null)

    /** The chosen engine's search prefix, for Enter on text that is not an address. */
    var searchPrefix by mutableStateOf(DEFAULT_SEARCH_URL)

    /** The completed host while the added part is still selected, else null. */
    val completedHost: String?
        get() {
            val selection = text.selection
            return if (!selection.collapsed && selection.min > 0 && selection.max == text.text.length) text.text.toString() else null
        }

    /**
     * Runs on the person's edits only, not on `setTextAndSelectAll` or `setTextAndPlaceCursorAtEnd`. The IME's
     * composing region is internal to the buffer in foundation 1.12.1, so the state's region before the edit is used.
     */
    val completion = object : InputTransformation {
        override fun TextFieldBuffer.transformInput() {
            val full = inlineCompletion(
                originalText.toString(), originalSelection, asCharSequence().toString(), selection,
                composing = text.composition != null,
                hosts = index?.hosts.orEmpty(),
            ) ?: return
            val typed = length
            append(full.substring(typed))
            selection = TextRange(typed, length)
        }
    }
}
