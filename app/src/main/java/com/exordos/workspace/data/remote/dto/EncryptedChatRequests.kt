package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.UIORequest
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.data.uio.MessageUIO
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

/** Dedicated opaque transport: service payloads never pass through Markdown requests. */
class EncryptedSendMessageRequest(
    streamUuid: String,
    topicUuid: String,
    payload: MessageResponsePayload,
    canonicalUuid: String,
) : UIORequest<MessageCreateData, MessageResponse, MessageUIO, ApiError> {
    override fun toUio(dto: MessageResponse): MessageUIO = dto.toUio()
    override val method = HTTPMethod.POST
    override val requiresApiKey = true
    override val url = "/api/workspace/v1/messenger/messages/"
    override val data = MessageCreateData(canonicalUuid, streamUuid, topicUuid, payload)
}

class EncryptedEditMessageRequest(messageUuid: String, payload: MessageResponsePayload) :
    UIORequest<EditMessageRequestData, MessageResponse, MessageUIO, ApiError> {
    override fun toUio(dto: MessageResponse): MessageUIO = dto.toUio()
    override val method = HTTPMethod.PUT
    override val requiresApiKey = true
    override val url = "/api/workspace/v1/messenger/messages/$messageUuid"
    override val data = EditMessageRequestData(payload)
}
