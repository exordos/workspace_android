package ru.genesiscorporation.workspace.beta.data.remote.dto

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
    override val url: String = "/user_uploads"
    override val data = SendMessageRequestData(
        type,
        to,
        content,
        topic
    )
}


class UploadFileRequestData()


@Serializable
data class UploadFileResponseData(
    val url: String,
    val filename: String
)