package ru.genesiscorporation.workspace.beta.data.navigation

import ru.genesiscorporation.workspace.beta.data.WorkspaceAccount
import java.net.URI
import java.util.UUID

data class WorkspaceDeepLink(
    val baseUrl: String?,
    val organizationId: String,
    val projectId: String,
    val target: WorkspaceDeepLinkTarget,
) {
    fun matches(account: WorkspaceAccount): Boolean =
        account.projectId.equals(projectId, ignoreCase = true) &&
            (
                baseUrl == null ||
                    canonicalWorkspaceBaseUrl(account.baseUrl) == baseUrl
            )
}

sealed interface WorkspaceDeepLinkTarget {
    data class Stream(val streamUuid: String) : WorkspaceDeepLinkTarget
    data class Topic(
        val streamUuid: String,
        val topicUuid: String,
    ) : WorkspaceDeepLinkTarget

    data class Message(val messageUuid: String) : WorkspaceDeepLinkTarget
}

fun parseWorkspaceDeepLink(value: String?): WorkspaceDeepLink? {
    val uri = value
        ?.takeIf { it.length <= MAX_DEEP_LINK_LENGTH }
        ?.let { runCatching { URI(it) }.getOrNull() }
        ?: return null
    if (
        uri.userInfo != null ||
        uri.host.isNullOrBlank() ||
        uri.rawQuery != null ||
        uri.rawFragment != null
    ) {
        return null
    }
    val baseUrl: String? = when {
        uri.scheme.equals("https", ignoreCase = true) ->
            canonicalWorkspaceBaseUrl(uri) ?: return null

        uri.scheme.equals(CUSTOM_SCHEME, ignoreCase = true) ->
            if (
                uri.host.equals(CUSTOM_SCHEME_HOST, ignoreCase = true) &&
                uri.port == -1
            ) {
                null
            } else {
                return null
            }

        else -> return null
    }
    val segments = uri.path
        ?.split('/')
        ?.filter(String::isNotEmpty)
        ?: return null
    if (
        segments.size !in setOf(6, 8) ||
        segments[0] != "org" ||
        segments[2] != "project"
    ) {
        return null
    }
    val organizationId = segments[1]
        .takeIf {
            it.length in 1..MAX_ORGANIZATION_ID_LENGTH &&
                ORGANIZATION_ID_PATTERN.matches(it) &&
                it != "." &&
                it != ".." &&
                ".." !in it
        }
        ?: return null
    val projectId = canonicalUuid(segments[3]) ?: return null
    val target = when (segments[4]) {
        "stream" -> {
            val streamUuid = canonicalUuid(segments[5]) ?: return null
            if (segments.size == 6) {
                WorkspaceDeepLinkTarget.Stream(streamUuid)
            } else {
                if (segments[6] != "topic") return null
                val topicUuid = canonicalUuid(segments[7]) ?: return null
                WorkspaceDeepLinkTarget.Topic(streamUuid, topicUuid)
            }
        }

        "message" -> {
            if (segments.size != 6) return null
            WorkspaceDeepLinkTarget.Message(
                canonicalUuid(segments[5]) ?: return null,
            )
        }

        else -> return null
    }
    return WorkspaceDeepLink(
        baseUrl = baseUrl,
        organizationId = organizationId,
        projectId = projectId,
        target = target,
    )
}

fun canonicalWorkspaceBaseUrl(value: String): String? =
    runCatching { URI(value) }
        .getOrNull()
        ?.let(::canonicalWorkspaceBaseUrl)

fun canonicalWorkspaceRealmUrl(value: String): String? {
    val uri = runCatching { URI(value) }.getOrNull() ?: return null
    if (
        uri.rawQuery != null ||
        uri.rawFragment != null ||
        (
            !uri.rawPath.isNullOrEmpty() &&
                uri.rawPath != "/"
        )
    ) {
        return null
    }
    return canonicalWorkspaceBaseUrl(uri)
}

private fun canonicalWorkspaceBaseUrl(uri: URI): String? {
    if (
        !uri.scheme.equals("https", ignoreCase = true) ||
        uri.host.isNullOrBlank() ||
        uri.userInfo != null
    ) {
        return null
    }
    return URI(
        "https",
        null,
        uri.host.lowercase(),
        uri.port,
        null,
        null,
        null,
    ).toString()
}

private fun canonicalUuid(value: String): String? =
    runCatching { UUID.fromString(value).toString() }.getOrNull()

private const val CUSTOM_SCHEME = "ew"
private const val CUSTOM_SCHEME_HOST = "open"
private const val MAX_DEEP_LINK_LENGTH = 2_048
private const val MAX_ORGANIZATION_ID_LENGTH = 255
private val ORGANIZATION_ID_PATTERN = Regex("""[A-Za-z0-9._-]+""")
