package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

@Serializable
data class EventRegistrationRequest(
    val fetchEventTypes: String,
    val narrow: String?
): ApiRequest<EventRegistrationRequestData, EventRegistrationResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/v1/register"
    override val data = EventRegistrationRequestData(
        fetchEventTypes, narrow
    )
}

@Serializable
data class EventRegistrationRequestData(
    val fetch_event_types: String,
    val narrow: String?
)

@Serializable
data class EventRegistrationResponse(
    val queue_id: String
)