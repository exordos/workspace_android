package com.exordos.workspace.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import com.exordos.workspace.data.remote.ApiError
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.HTTPMethod

@Serializable
data class EditMessageRequest(
    val messageId: String,
    val content: String
): ApiRequest<EditMessageRequestData, EditMessageResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.PUT
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/workspace/v1/messenger/messages/${messageId}"
    override val data = EditMessageRequestData(MessageResponsePayload("markdown", content))
}

@Serializable
data class EditMessageRequestData(
    @Serializable(with = MessageBodyPayloadSerializer::class) val payload: MessageResponsePayload
)

@Serializable
data class EditMessageResponse(
    val uuid: String
)
/** Ordinary edits have always included content, even when empty; service kinds omit it. */
internal object MessageBodyPayloadSerializer : JsonTransformingSerializer<MessageResponsePayload>(MessageResponsePayload.serializer()) {
    override fun transformSerialize(element: JsonElement): JsonElement =
        if (element.jsonObject["kind"]?.jsonPrimitive?.content == "markdown")
            JsonObject(element.jsonObject + ("content" to JsonPrimitive(element.jsonObject["content"]?.jsonPrimitive?.content.orEmpty())))
        else element
}
