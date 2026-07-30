package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class UploadFileRequest(
    val type: String,
    val to: String,
    val content: String,
    val topic: String?
): ApiRequest<SendMessageRequestData, UploadFileResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/files/"
    override val data = SendMessageRequestData(
        type,
        to,
        MessageResponsePayload("markdown", content)
    )
}


class UploadFileRequestData()


@Serializable
data class UploadFileResponseData(
    val uuid: String,
    val name: String,
    @SerialName("content_type")
    val contentType: String = "",
    @SerialName("size_bytes")
    val sizeBytes: Long? = null,
)
