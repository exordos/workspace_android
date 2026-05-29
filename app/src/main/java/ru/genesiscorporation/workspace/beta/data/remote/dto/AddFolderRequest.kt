package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class AddFolderRequest(
    title: String
): ApiRequest<AddFolderRequestData, AddFolderResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val shouldApplySuffix: Boolean = false
    override val isJson: Boolean = true
    override val url: String = "/workspace/v1/folders/"
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