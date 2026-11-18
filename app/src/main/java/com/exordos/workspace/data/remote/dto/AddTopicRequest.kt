package com.exordos.workspace.data.remote.dto

import com.exordos.workspace.data.remote.UIORequest
import com.exordos.workspace.data.uio.toUio
import com.exordos.workspace.data.uio.TopicUIO
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

class AddTopicRequest(
    val name: String,
    val streamUuid: String
): UIORequest<AddTopicRequestData, TopicsResponseData, TopicUIO, ApiError> {
    override fun toUio(dto: TopicsResponseData): TopicUIO = dto.toUio()
    override val method: HTTPMethod = HTTPMethod.POST
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/stream_topics/"
    override val data = AddTopicRequestData(
        name, streamUuid
    )
}

@Serializable
data class AddTopicRequestData(
    val name: String,
    @SerialName("stream_uuid") val streamUuid: String
)
