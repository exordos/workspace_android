package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

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
