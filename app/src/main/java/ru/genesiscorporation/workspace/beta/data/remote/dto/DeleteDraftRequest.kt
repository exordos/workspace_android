package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class DeleteDraftRequest(
    draftUuid: String,
    revision: Int
): ApiRequest<EmptyRequestData, DeleteDraftResponseData, ApiError> {
    override val method: HTTPMethod = HTTPMethod.DELETE
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/drafts/${draftUuid}"
    override val data = EmptyRequestData()
    override val additionalHeaders: Map<String, String> = mapOf("If-Match" to """"$revision"""")
}

@Serializable
class DeleteDraftResponseData()