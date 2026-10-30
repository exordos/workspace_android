package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.EmptyRequestData
import com.exordos.workspace.data.remote.HTTPMethod
import java.util.UUID

class DeleteMessageRequest(
    messageUuid: String,
) : ApiRequest<EmptyRequestData, String, ApiError> {
    private val canonicalMessageUuid = requireNotNull(
        parseCanonicalUuid(messageUuid),
    ) {
        "messageUuid must be a canonical UUID"
    }

    override val method = HTTPMethod.DELETE
    override val url =
        "/api/workspace/v1/messenger/messages/$canonicalMessageUuid"
    override val data = EmptyRequestData()
}

internal fun parseCanonicalUuid(value: String): String? {
    val trimmed = value.trim()
    val canonical = runCatching { UUID.fromString(trimmed).toString() }
        .getOrNull()
        ?: return null
    return canonical.takeIf { it == trimmed.lowercase() }
}
