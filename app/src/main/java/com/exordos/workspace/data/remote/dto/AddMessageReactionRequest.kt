package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class AddMessageReactionRequest(
    messageUuid: String,
    emojiName: String
): ApiRequest<AddMessageReactionRequestData, AddMessageReactionResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/message_reactions/"
    override val data = AddMessageReactionRequestData(messageUuid, emojiName)
}

@Serializable
data class AddMessageReactionRequestData(
    @SerialName("message_uuid") val messageUuid: String,
    @SerialName("emoji_name")  val emojiName: String
)

@Serializable
data class AddMessageReactionResponse(
    val uuid: String,
    @SerialName("user_uuid") val userUuid: String,
    @SerialName("emoji_name") val emojiName: String,
    @SerialName("message_uuid") val messageUuid: String,
)
