package com.exordos.workspace.data.remote

/** One protected operation uses one credential snapshot, including after a delayed HTTP 401. */
class ProtectedRequestAuthentication(private val accessToken: String) {
    init { require(accessToken.isNotBlank()) }

    fun matches(other: ProtectedRequestAuthentication?): Boolean = other != null && accessToken == other.accessToken
    fun matchesAccessToken(current: String?): Boolean = current == accessToken

    fun <D, R, U, E> pin(request: UIORequest<D, R, U, E>): UIORequest<D, R, U, E> =
        object : UIORequest<D, R, U, E> by request {
            override val requiresApiKey = false
            override val additionalHeaders = request.additionalHeaders.filterKeys { !it.equals("Authorization", ignoreCase = true) } +
                ("Authorization" to "Bearer $accessToken")
        }

    fun <D, R, E> pin(request: ApiRequest<D, R, E>): ApiRequest<D, R, E> = object : ApiRequest<D, R, E> by request {
        // WorkspaceAPIClient must not refresh/replay this operation using replacement credentials.
        override val requiresApiKey = false
        override val additionalHeaders = request.additionalHeaders.filterKeys { !it.equals("Authorization", ignoreCase = true) } +
            ("Authorization" to "Bearer $accessToken")
    }

    override fun toString(): String = "ProtectedRequestAuthentication([REDACTED])"
}
