package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.UIORequest
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.data.uio.DraftUIO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

class UpdateDraftRequest(
    draftUuid: String,
    content: String,
    revision: Int
): UIORequest<UpdateDraftRequestData, Draft, DraftUIO, ApiError> {
    override fun toUio(dto: Draft): DraftUIO = dto.toUio()
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/drafts/${draftUuid}"
    override val data = UpdateDraftRequestData(
        MessageResponsePayload("markdown", content)
    )
    override val additionalHeaders: Map<String, String> = mapOf("If-Match" to """"$revision"""")
}

@Serializable
data class UpdateDraftRequestData(
    @Serializable(with = MessageBodyPayloadSerializer::class) val payload: MessageResponsePayload
)
