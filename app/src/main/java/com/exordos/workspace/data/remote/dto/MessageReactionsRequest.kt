package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class MessageReactionsRequest(
    userUuid: String
): ApiRequest<MessageReactionsRequestData, List<MessageReaction>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/message_reactions/"
    override val data = MessageReactionsRequestData(userUuid)
}

@Serializable
data class MessageReactionsRequestData(
    @SerialName("user_uuid") val userUuid: String
)

@Serializable
data class MessageReaction(
    val uuid: String,
    @SerialName("user_uuid") val userUuid: String,
    @SerialName("emoji_name") val emojiName: String,
    @SerialName("message_uuid") val messageUuid: String,
)
