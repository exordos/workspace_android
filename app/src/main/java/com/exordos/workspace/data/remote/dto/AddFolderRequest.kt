package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class AddFolderRequest(
    title: String
): ApiRequest<AddFolderRequestData, AddFolderResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val isJson: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/folders/"
    override val data = AddFolderRequestData(title)
}

@Serializable
data class AddFolderRequestData(
    val title: String,
//    @SerialName("background_color_value") val backgroundColorValue: Long
)
@Serializable
data class AddFolderResponseData(
    val uuid: String
)
