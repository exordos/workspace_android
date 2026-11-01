package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

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
    val name: String
)

@Serializable
data class UploadAvatarResponseData(
    val uuid: String
)
