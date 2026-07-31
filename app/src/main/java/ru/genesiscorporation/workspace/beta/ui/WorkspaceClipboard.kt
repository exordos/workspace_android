package ru.genesiscorporation.workspace.beta.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle

internal fun copyPlainWorkspaceText(
    context: Context,
    label: String,
    value: String,
    sensitive: Boolean = true,
): Boolean {
    if (value.isBlank()) return false
    val clipboard = context.getSystemService(ClipboardManager::class.java)
        ?: return false
    return runCatching {
        val clip = ClipData.newPlainText(
            label.take(MAX_WORKSPACE_CLIPBOARD_LABEL_CHARS),
            value,
        )
        if (sensitive && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        clipboard.setPrimaryClip(clip)
    }.isSuccess
}

private const val MAX_WORKSPACE_CLIPBOARD_LABEL_CHARS = 64
