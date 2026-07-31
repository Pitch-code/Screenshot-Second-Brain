package com.shelfie.core.designsystem.component

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource

/**
 * A user-facing message referenced by resource id rather than as literal text.
 *
 * ViewModels must not build display strings: they have no Context, and a string
 * assembled there cannot be localised. So they emit one of these and the
 * composable resolves it against the current configuration.
 */
sealed interface UiMessage {

    data class Text(
        @StringRes val resId: Int,
        val args: List<Any> = emptyList(),
    ) : UiMessage

    data class Plural(
        @PluralsRes val resId: Int,
        val quantity: Int,
        val args: List<Any> = emptyList(),
    ) : UiMessage
}

/** Resolves a [UiMessage] to displayable text. */
@Composable
fun UiMessage.resolve(): String = when (this) {
    is UiMessage.Text ->
        if (args.isEmpty()) stringResource(resId) else stringResource(resId, *args.toTypedArray())

    is UiMessage.Plural ->
        if (args.isEmpty()) {
            pluralStringResource(resId, quantity)
        } else {
            pluralStringResource(resId, quantity, *args.toTypedArray())
        }
}
