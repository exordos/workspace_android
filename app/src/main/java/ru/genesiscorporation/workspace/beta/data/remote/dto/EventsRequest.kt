package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

data class EventsRequest(
    val afterEpochVersion: Int,
    val epochGeneration: String,
    val pageLimit: Int = DEFAULT_EVENTS_PAGE_SIZE,
) : ApiRequest<EventsRequestData, List<JsonObject>, ApiError> {
    init {
        require(afterEpochVersion >= 0) {
            "Event cursor must not be negative"
        }
        require(epochGeneration.isNotBlank()) {
            "Event cursor generation must not be blank"
        }
        require(epochGeneration.length <= MAX_EPOCH_GENERATION_CHARS) {
            "Event cursor generation is too long"
        }
        require(pageLimit in 1..MAX_EVENTS_PAGE_SIZE) {
            "Event page size must be between 1 and $MAX_EVENTS_PAGE_SIZE"
        }
    }

    override val method = HTTPMethod.GET
    override val url = "/api/workspace/v1/events/"
    override val data = EventsRequestData(
        afterEpochVersion = afterEpochVersion,
        epochGeneration = epochGeneration,
        pageLimit = pageLimit,
    )
}

@Serializable
data class EventsRequestData(
    @SerialName("epoch_version>") val afterEpochVersion: Int,
    @SerialName("epoch_generation") val epochGeneration: String,
    @SerialName("page_limit") val pageLimit: Int,
)

const val DEFAULT_EVENTS_PAGE_SIZE = 500
private const val MAX_EVENTS_PAGE_SIZE = 500
private const val MAX_EPOCH_GENERATION_CHARS = 256
