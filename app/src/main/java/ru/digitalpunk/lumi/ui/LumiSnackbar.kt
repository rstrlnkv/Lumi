package ru.digitalpunk.lumi.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarVisuals

/** A snackbar whose action reads [actionDescription] to TalkBack instead of its short label ("Edit" reads "Edit bookmark"). */
class LumiSnackbarVisuals(
    override val message: String,
    override val actionLabel: String? = null,
    val actionDescription: String? = null,
    override val duration: SnackbarDuration = SnackbarDuration.Short,
    override val withDismissAction: Boolean = false,
) : SnackbarVisuals
