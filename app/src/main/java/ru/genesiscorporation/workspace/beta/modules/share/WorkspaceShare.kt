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
    val target = workspaceShareTarget(baseUrl) ?: return null
    if (!isShareUuid(projectUuid) || !isShareUuid(streamUuid)) return null
    return "${target.origin}/org/${target.organizationRouteId}/project/$projectUuid/stream/$streamUuid"
}

internal fun workspaceUserShareLink(baseUrl: String?, userUuid: String): String? {
    val target = workspaceShareTarget(baseUrl) ?: return null
    if (!isShareUuid(userUuid)) return null
    return "${target.origin}/#user/$userUuid"
}

internal fun shareWorkspaceLink(context: Context, title: String, link: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TITLE, title)
        putExtra(Intent.EXTRA_TEXT, link)
    }
    context.startActivity(Intent.createChooser(intent, "Поделиться"))
}

private data class WorkspaceShareTarget(
    val origin: String,
    val organizationRouteId: String
)

private fun workspaceShareTarget(baseUrl: String?): WorkspaceShareTarget? {
    val uri = baseUrl?.trim()?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    if (uri.scheme?.lowercase() !in setOf("http", "https") || uri.host.isNullOrBlank() ||
        uri.userInfo != null || uri.rawQuery != null || uri.rawFragment != null ||
        uri.rawPath.orEmpty().trim('/').isNotEmpty()
    ) return null
    return WorkspaceShareTarget(
        origin = uri.toString().trimEnd('/'),
        organizationRouteId = workspaceOrganizationRouteId(uri)
    )
}

// Shared URL contract: keep this byte-identical to workspace_ui's buildOrgRouteIdFromOrigin.
private fun workspaceOrganizationRouteId(uri: URI): String {
    val hasNonDefaultPort = uri.port >= 0 &&
        !(uri.scheme.equals("http", ignoreCase = true) && uri.port == 80) &&
        !(uri.scheme.equals("https", ignoreCase = true) && uri.port == 443)
    val portSuffix = uri.port.takeIf { hasNonDefaultPort }?.let { "-$it" }.orEmpty()
    return "${uri.host.lowercase()}$portSuffix"
        .replace(Regex("[^a-z0-9.-]"), "-")
        .replace(Regex("-+"), "-")
        .trim('-')
        .ifEmpty { "org" }
}

private fun isShareUuid(value: String): Boolean =
    runCatching { UUID.fromString(value).toString().equals(value, ignoreCase = true) }
        .getOrDefault(false)
