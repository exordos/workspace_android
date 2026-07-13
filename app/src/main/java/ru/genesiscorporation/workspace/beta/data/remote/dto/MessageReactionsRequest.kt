package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class MessageReactionsRequest(): ApiRequest<EmptyRequestData, List<MessageReaction>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/messenger/v1/streams/"
    override val data = EmptyRequestData()
}

@Serializable
data class MessageReaction(
    val uuid: String,
    @SerialName("user_uuid") val userUuid: String,
    @SerialName("emoji_name") val emojiName: String,
    @SerialName("message_uuid") val messageUuid: String,
)