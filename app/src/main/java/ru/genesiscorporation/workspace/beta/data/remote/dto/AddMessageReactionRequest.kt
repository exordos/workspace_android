package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class AddMessageReactionRequest(
    messageUuid: String,
    emojiName: String
): ApiRequest<AddMessageReactionRequestData, AddMessageReactionResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/messenger/v1/message_reactions/"
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