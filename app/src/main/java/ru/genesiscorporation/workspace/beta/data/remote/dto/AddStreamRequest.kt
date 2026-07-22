package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class AddStreamRequest(
    val name: String,
    val description: String,
    val directUserUuid: String?
): ApiRequest<AddStreamRequestData, Stream, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/streams/"
    override val data = AddStreamRequestData(
        name, description, directUserUuid, "native"
    )
}

@Serializable
data class AddStreamRequestData(
    val name: String,
    val description: String,
    @SerialName("direct_user_uuid") val directUserUuid: String?,
    @SerialName("source_name") val sourceName: String?,
    val source: AddStreamRequestSource = AddStreamRequestSource()
)
@Serializable
data class AddStreamRequestSource(
    val kind: String = "native"
)