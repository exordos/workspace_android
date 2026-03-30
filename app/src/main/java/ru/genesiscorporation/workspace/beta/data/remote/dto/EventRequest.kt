package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

data class EventRequest(
    val queueId: String,
    val lastEventId: String
): ApiRequest<EventRequestData, String, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/v1/events"
    override val data = EventRequestData(
        queueId, lastEventId
    )
}

@Serializable
data class EventRequestData(
    val queue_id: String,
    val last_event_id: String
)
@Serializable
data class EventResponse(
    val events: List<EventResponseData>
)
@Serializable
data class EventResponseData(
    val id: Int
)