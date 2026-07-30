package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class AddStreamRequest(
    name: String,
    description: String,
    directUserUuid: String?,
    inviteOnly: Boolean? = null,
    announce: Boolean? = null,
): ApiRequest<AddStreamRequestData, Stream, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/streams/"
    override val data = AddStreamRequestData(
        name = name,
        description = description,
        directUserUuid = directUserUuid,
        inviteOnly = inviteOnly,
        announce = announce,
    )
}

@Serializable
data class AddStreamRequestData(
    val name: String,
    val description: String,
    @SerialName("direct_user_uuid") val directUserUuid: String? = null,
    @SerialName("source_name") val sourceName: String = "native",
    val source: AddStreamRequestSource = AddStreamRequestSource(),
    @SerialName("invite_only") val inviteOnly: Boolean? = null,
    val announce: Boolean? = null,
)
@Serializable
data class AddStreamRequestSource(
    val kind: String = "native"
)
