package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class EditMessageRequest(
    val messageId: Int,
    val content: String
): ApiRequest<EditMessageRequestData, EditMessageResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.PATCH
    override val requiresApiKey: Boolean = true
    override val url: String = "/messages/${messageId}"
    override val data = EditMessageRequestData(content)
}

@Serializable
data class EditMessageRequestData(
    val content: String
)
@Serializable
data class EditMessageResponse(
    val result: String
)