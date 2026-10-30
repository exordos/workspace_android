package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

class RemoveMessageReactionRequest(
    reactionUuid: String
): ApiRequest<EmptyRequestData, AddMessageReactionResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.DELETE
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/message_reactions/${reactionUuid}"
    override val data = EmptyRequestData()
}

@Serializable
data class DeletedMessageReaction(
    val uuid: String,
    @SerialName("user_uuid") val userUuid: String
)
