package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class EditMessageRequest(
    val messageId: String,
    val content: String
): ApiRequest<EditMessageRequestData, EditMessageResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.PUT
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/messages/${messageId}"
    override val data = EditMessageRequestData(EditMessageRequestPayload("markdown", content))
}

@Serializable
data class EditMessageRequestData(
    val payload: EditMessageRequestPayload
)

@Serializable
data class EditMessageRequestPayload(
    val kind: String,
    val content: String
)
@Serializable
data class EditMessageResponse(
    val uuid: String
)