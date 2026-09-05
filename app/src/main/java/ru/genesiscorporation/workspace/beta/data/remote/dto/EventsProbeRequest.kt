package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class EventsProbeRequest(
    afterEpochVersion: Int,
    epochGeneration: String
) : ApiRequest<EventsProbeRequestData, List<EventsProbeResponseData>, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/events/"
    override val data = EventsProbeRequestData(
        afterEpochVersion = afterEpochVersion,
        epochGeneration = epochGeneration
    )
}

@Serializable
data class EventsProbeRequestData(
    @SerialName("epoch_version>") val afterEpochVersion: Int,
    @SerialName("epoch_generation") val epochGeneration: String,
    @SerialName("page_limit") val pageLimit: Int = 1
)

@Serializable
data class EventsProbeResponseData(
    @SerialName("epoch_version") val epochVersion: Int
)
