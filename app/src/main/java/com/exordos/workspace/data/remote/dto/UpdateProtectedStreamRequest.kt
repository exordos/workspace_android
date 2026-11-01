package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod

/** The metadata endpoint uses PUT with partial fields; stream encryption/type are immutable. */
class UpdateProtectedStreamRequest(streamUuid: String, name: String, description: String) :
    ApiRequest<UpdateProtectedStreamRequestData, Stream, ApiError> {
    override val method = HTTPMethod.PUT
    override val url = "/api/workspace/v1/messenger/streams/${canonicalStreamId(streamUuid)}"
    override val data = UpdateProtectedStreamRequestData(name, description)
}

@Serializable
data class UpdateProtectedStreamRequestData(val name: String, val description: String)

class ProtectedStreamDetailsRequest(streamUuid: String) : ApiRequest<EmptyRequestData, Stream, ApiError> {
    override val method = HTTPMethod.GET
    override val url = "/api/workspace/v1/messenger/streams/${canonicalStreamId(streamUuid)}"
    override val data = EmptyRequestData()
}

class DeleteProtectedStreamRequest(streamUuid: String) : ApiRequest<EmptyRequestData, String, ApiError> {
    override val method = HTTPMethod.DELETE
    override val url = "/api/workspace/v1/messenger/streams/${canonicalStreamId(streamUuid)}"
    override val data = EmptyRequestData()
}

internal fun canonicalStreamId(value: String): String {
    require(value == value.trim()) { "Invalid stream identifier" }
    return requireNotNull(parseCanonicalUuid(value)) { "Invalid stream identifier" }
}
