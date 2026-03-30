package ru.genesiscorporation.workspace.beta.data.remote.dto

import kotlinx.serialization.Serializable
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiRequest
import ru.genesiscorporation.workspace.beta.data.remote.EmptyRequestData
import ru.genesiscorporation.workspace.beta.data.remote.HTTPMethod

class SubscriptionsRequest(): ApiRequest<EmptyRequestData, SubscriptionsResponse, ApiError> {
    override val method: HTTPMethod = HTTPMethod.GET
    override val requiresApiKey: Boolean = true
    override val url: String = "/api/v1/users/me/subscriptions"
    override val data = EmptyRequestData()
}

class SubscriptionsRequestData()

@Serializable
data class SubscriptionsResponse(
    val subscriptions: List<Subscription>
)

@Serializable
data class Subscription(
    val stream_id: Int,
    val name: String
)