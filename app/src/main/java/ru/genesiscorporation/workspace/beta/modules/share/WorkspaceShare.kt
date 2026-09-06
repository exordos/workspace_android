package ru.genesiscorporation.workspace.beta.modules.share

import android.content.Context
import android.content.Intent
import java.net.URI
import java.util.UUID

internal fun workspaceStreamShareLink(
    baseUrl: String?,
    projectUuid: String,
    streamUuid: String
): String? {
    val origin = workspaceShareOrigin(baseUrl) ?: return null
    if (!isShareUuid(projectUuid) || !isShareUuid(streamUuid)) return null
    return "$origin/project/$projectUuid/stream/$streamUuid"
}

internal fun workspaceUserShareLink(baseUrl: String?, userUuid: String): String? {
    val origin = workspaceShareOrigin(baseUrl) ?: return null
    if (!isShareUuid(userUuid)) return null
    return "$origin/#user/$userUuid"
}

internal fun shareWorkspaceLink(context: Context, title: String, link: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_TEXT, link)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться"))
}

private fun workspaceShareOrigin(baseUrl: String?): String? {
    val uri = baseUrl?.trim()?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() ||
        uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
        uri.rawPath.orEmpty().trim('/').isNotEmpty()
    ) return null
    return uri.toString().trimEnd('/')
}

private fun isShareUuid(value: String): Boolean =
    runCatching { UUID.fromString(value).toString().equals(value, ignoreCase = true) }
        .getOrDefault(false)
