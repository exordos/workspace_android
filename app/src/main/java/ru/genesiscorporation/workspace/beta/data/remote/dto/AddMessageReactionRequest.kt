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

internal fun validateAddMessageReactionResponse(
    response: AddMessageReactionResponse,
    requestedMessageUuid: String,
    expectedUserUuid: String,
): MessageReaction? {
    val reactionUuid = parseCanonicalMessageUuid(response.uuid)
        ?: return null
    val messageUuid = parseCanonicalMessageUuid(response.messageUuid)
        ?: return null
    val userUuid = parseCanonicalMessageUuid(response.userUuid)
        ?: return null
    if (
        messageUuid != parseCanonicalMessageUuid(requestedMessageUuid) ||
        userUuid != parseCanonicalMessageUuid(expectedUserUuid)
    ) {
        return null
    }
    val emojiName = response.emojiName
        .trim()
        .takeIf {
            it.length in 1..MAX_REACTION_EMOJI_NAME_CHARS &&
                it.none(Char::isISOControl)
        }
        ?: return null
    return MessageReaction(
        uuid = reactionUuid,
        userUuid = userUuid,
        emojiName = emojiName,
        messageUuid = messageUuid,
    )
}

private const val MAX_REACTION_EMOJI_NAME_CHARS = 128
