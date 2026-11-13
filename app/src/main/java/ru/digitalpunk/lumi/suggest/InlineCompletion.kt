package ru.digitalpunk.lumi.suggest

import androidx.compose.ui.text.TextRange

/**
 * Safari-style completion: [text] plus the rest of the first of [hosts] (best first) that starts with it, or null.
 * Only when the IME is not composing, the caret is at the end, the edit added one or two characters (a selected
 * completion it replaced does not count as removed), and [text] has no whitespace, `/` or `:`.
 */
fun inlineCompletion(prev: String, prevSelection: TextRange, text: String, caret: TextRange, composing: Boolean, hosts: List<String>): String? {
    if (composing || text.isEmpty() || !caret.collapsed || caret.end != text.length) return null
    if (text.length - (prev.length - prevSelection.length) !in 1..2) return null
    if (text.any { it.isWhitespace() || it == '/' || it == ':' }) return null
    val host = hosts.firstOrNull { it.length > text.length && it.startsWith(text, ignoreCase = true) } ?: return null
    return text + host.substring(text.length)
}

/** What the person typed: [text] without a selected tail (the inline completion). */
fun typedPart(text: String, selection: TextRange): String =
    if (!selection.collapsed && selection.max == text.length) text.substring(0, selection.min) else text
