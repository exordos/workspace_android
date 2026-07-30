package ru.genesiscorporation.workspace.beta.data.remote.dto

import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

data class MarkMessagesReadRequest(
    val messageUuid: String,
): ApiRequest<EmptyRequestData, MessageResponse, ApiError> {
    private val canonicalMessageUuid = requireNotNull(
        parseCanonicalMessageUuid(messageUuid),
    ) {
        "messageUuid must be a canonical UUID"
    }

    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String =
        "/api/workspace/v1/messenger/messages/" +
            "$canonicalMessageUuid/actions/read_up_to/invoke"
    override val data = EmptyRequestData()
}
