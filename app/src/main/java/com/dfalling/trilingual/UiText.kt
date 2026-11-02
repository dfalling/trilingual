package com.dfalling.trilingual

import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A string resource and its format args, kept unresolved so code outside the UI can produce
 * user-facing text that's rendered in the phone's language when it's shown. Args may be
 * [UiText] themselves.
 */
class UiText(
    @param:StringRes val id: Int,
    vararg val args: Any,
) {
    fun resolve(resources: Resources): String = resources.getString(id, *args.map { if (it is UiText) it.resolve(resources) else it }.toTypedArray())

    @Composable
    fun asString(): String = stringResource(id, *args.map { if (it is UiText) it.asString() else it }.toTypedArray())
}
