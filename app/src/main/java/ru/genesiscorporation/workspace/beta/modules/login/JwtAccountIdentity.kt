package ru.genesiscorporation.workspace.beta.modules.login

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.UUID

internal fun userUuidFromAccessToken(
    accessToken: String,
    json: Json = Json { ignoreUnknownKeys = true },
): String? = uuidClaimFromAccessToken(
    accessToken = accessToken,
    claimNames = USER_ID_CLAIMS,
    json = json,
)

internal fun projectUuidFromAccessToken(
    accessToken: String,
    json: Json = Json { ignoreUnknownKeys = true },
): String? = uuidClaimFromAccessToken(
    accessToken = accessToken,
    claimNames = PROJECT_ID_CLAIMS,
    json = json,
)

internal fun accessTokenMatchesAccount(
    accessToken: String,
    expectedUserId: String,
    expectedProjectId: String,
): Boolean {
    val expectedUserUuid = canonicalUuid(expectedUserId) ?: return false
    val expectedProjectUuid = canonicalUuid(expectedProjectId) ?: return false
    val actualUserUuid = userUuidFromAccessToken(accessToken) ?: return false
    val actualProjectUuid = projectUuidFromAccessToken(accessToken)
    return actualUserUuid == expectedUserUuid &&
        (actualProjectUuid == null || actualProjectUuid == expectedProjectUuid)
}

private fun uuidClaimFromAccessToken(
    accessToken: String,
    claimNames: List<String>,
    json: Json,
): String? {
    val payload = accessToken.split('.').getOrNull(1) ?: return null
    val decoded = runCatching {
        String(
            Base64.getUrlDecoder().decode(payload),
            StandardCharsets.UTF_8,
        )
    }.getOrNull() ?: return null
    val claims = runCatching {
        json.parseToJsonElement(decoded) as? JsonObject
    }.getOrNull() ?: return null
    return claimNames
        .asSequence()
        .mapNotNull { key -> claims[key]?.jsonPrimitive?.contentOrNull }
        .mapNotNull { value ->
            runCatching { UUID.fromString(value.trim()).toString() }.getOrNull()
        }
        .firstOrNull()
}

private fun canonicalUuid(value: String): String? =
    runCatching { UUID.fromString(value.trim()).toString() }.getOrNull()

private val USER_ID_CLAIMS = listOf("sub", "user_uuid", "user_id", "uuid")
private val PROJECT_ID_CLAIMS = listOf("project_id", "project_uuid", "project")
