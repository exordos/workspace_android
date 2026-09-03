package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class UpdateDraftRequest(
    draftUuid: String,
    content: String,
    revision: Int
): ApiRequest<UpdateDraftRequestData, Draft, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/drafts/${draftUuid}"
    override val data = UpdateDraftRequestData(
        EditMessageRequestPayload("markdown", content)
    )
    override val additionalHeaders: Map<String, String> = mapOf("If-Match" to """"$revision"""")
}

@Serializable
data class UpdateDraftRequestData(
    val payload: EditMessageRequestPayload
)