package ru.genesiscorporation.workspace.beta.data

import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64

@Serializable
data class WorkspaceAccount(
    val accountId: String,
    val baseUrl: String,
    val projectId: String,
    val projectName: String,
    val organizationName: String? = null,
    val userId: String,
    val login: String,
    val displayName: String? = null,
    val email: String? = null,
    val avatarUrn: String? = null,
)

fun buildWorkspaceAccountId(
    baseUrl: String,
    projectId: String,
    userId: String,
): String {
    val canonicalIdentity = listOf(
        baseUrl.trim().trimEnd('/').lowercase(),
        projectId.trim().lowercase(),
        userId.trim().lowercase(),
    ).joinToString("\u0000")
    return workspaceStorageKey(canonicalIdentity)
}

fun workspaceStorageKey(ownerKey: String): String {
    return Base64.getUrlEncoder().withoutPadding().encodeToString(
        MessageDigest.getInstance("SHA-256").digest(
            ownerKey.toByteArray(StandardCharsets.UTF_8),
        ),
    )
}

internal fun WorkspaceAccount.withProfile(
    userId: String,
    displayName: String,
    email: String?,
    avatarUrn: String?,
): WorkspaceAccount {
    if (this.userId != userId) return this
    return copy(
        displayName = displayName.trim().takeIf(String::isNotEmpty),
        email = email?.trim()?.takeIf(String::isNotEmpty),
        avatarUrn = avatarUrn?.trim()?.takeIf(String::isNotEmpty),
    )
}
