package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

/** Dedicated opaque transport: service payloads never pass through Markdown requests. */
class EncryptedSendMessageRequest(
    streamUuid: String,
    topicUuid: String,
    payload: MessageResponsePayload,
    canonicalUuid: String,
) : ApiRequest<MessageCreateData, MessageResponse, ApiError> {
    override val method = HTTPMethod.POST
    override val requiresApiKey = true
    override val url = "/api/workspace/v1/messenger/messages/"
    override val data = MessageCreateData(canonicalUuid, streamUuid, topicUuid, payload)
}

class EncryptedEditMessageRequest(messageUuid: String, payload: MessageResponsePayload) :
    ApiRequest<EditMessageRequestData, MessageResponse, ApiError> {
    override val method = HTTPMethod.PUT
    override val requiresApiKey = true
    override val url = "/api/workspace/v1/messenger/messages/$messageUuid"
    override val data = EditMessageRequestData(payload)
}
