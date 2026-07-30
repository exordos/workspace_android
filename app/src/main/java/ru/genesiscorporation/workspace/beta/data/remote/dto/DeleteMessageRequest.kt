package ru.genesiscorporation.workspace.beta.data.remote.dto

import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod
import java.util.UUID

class DeleteMessageRequest(
    messageUuid: String,
) : ApiRequest<EmptyRequestData, String, ApiError> {
    private val canonicalMessageUuid = requireNotNull(
        parseCanonicalMessageUuid(messageUuid),
    ) {
        "messageUuid must be a canonical UUID"
    }

    override val method = HTTPMethod.DELETE
    override val url =
        "/api/workspace/v1/messenger/messages/$canonicalMessageUuid"
    override val data = EmptyRequestData()
}

internal fun parseCanonicalMessageUuid(value: String): String? {
    val trimmed = value.trim()
    val canonical = runCatching { UUID.fromString(trimmed).toString() }
        .getOrNull()
        ?: return null
    return canonical.takeIf { it == trimmed.lowercase() }
}
