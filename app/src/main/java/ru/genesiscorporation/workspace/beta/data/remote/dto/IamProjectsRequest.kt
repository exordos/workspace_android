package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class IamProjectsRequest(
    accessToken: String,
) : ApiRequest<EmptyRequestData, String, ApiError> {
    override val method = HTTPMethod.GET
    override val requiresApiKey = false
    override val shouldApplySuffix = false
    override val url = "/api/core/v1/iam/projects/"
    override val data = EmptyRequestData()
    override val additionalHeaders = mapOf(
        "Accept" to "application/json",
        "Authorization" to "Bearer $accessToken",
    )
}

@Serializable
data class WorkspaceProject(
    val uuid: String,
    val name: String,
    val description: String? = null,
    val organizationName: String? = null,
)

fun parseWorkspaceProjects(
    responseBody: String,
    json: Json = Json { ignoreUnknownKeys = true },
): List<WorkspaceProject> {
    val root = json.parseToJsonElement(responseBody)
    val collection = when (root) {
        is JsonArray -> root
        is JsonObject -> PROJECT_COLLECTION_KEYS
            .asSequence()
            .mapNotNull { key -> root[key] as? JsonArray }
            .firstOrNull()
            ?: throw IllegalArgumentException("Expected IAM projects collection")
        else -> throw IllegalArgumentException("Expected IAM projects collection")
    }
    return collection
        .mapNotNull(::parseWorkspaceProject)
        .distinctBy(WorkspaceProject::uuid)
}

private fun parseWorkspaceProject(element: JsonElement): WorkspaceProject? {
    val project = element as? JsonObject ?: return null
    val uuid = project.requiredString("uuid") ?: return null
    val name = project.requiredString("name") ?: return null
    if (project.requiredString("status") == null) return null
    val organizationName = (project["organization"] as? JsonObject)
        ?.nullableString("name")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
    return WorkspaceProject(
        uuid = uuid,
        name = name,
        description = project.nullableString("description"),
        organizationName = organizationName,
    )
}

private fun JsonObject.requiredString(key: String): String? =
    nullableString(key)?.trim()?.takeIf(String::isNotEmpty)

private fun JsonObject.nullableString(key: String): String? =
    this[key]?.jsonPrimitive?.contentOrNull

private val PROJECT_COLLECTION_KEYS = listOf("items", "data", "results", "objects")
