package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class MarkMessagesReadRequest(
    val messageIds: List<Int>
): ApiRequest<MarkMessagesReadRequestData, MarkMessagesResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/messages/flags"
    override val data = MarkMessagesReadRequestData(
        "[${messageIds.joinToString(",")}]", "add", "read"
    )
}

@Serializable
data class MarkMessagesReadRequestData(
    val messages: String,
    val op: String,
    val flag: String
)

@Serializable
data class MarkMessagesResponseData(
    val messages: List<Int>
)