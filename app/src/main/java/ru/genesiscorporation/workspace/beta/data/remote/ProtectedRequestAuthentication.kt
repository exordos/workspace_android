package ru.genesiscorporation.workspace.beta.data.remote

/** One protected operation uses one credential snapshot, including after a delayed HTTP 401. */
class ProtectedRequestAuthentication(private val accessToken: String) {
    init { require(accessToken.isNotBlank()) }

    fun matches(other: ProtectedRequestAuthentication?): Boolean = other != null && accessToken == other.accessToken
    fun matchesAccessToken(current: String?): Boolean = current == accessToken

    /** Refresh the still-current session on 401, but never replay this captured operation.
     * The caller must verify the owner and start a fresh query after credentials change.
     * Recovery is opt-in so protected mutations retain their no-refresh/no-replay policy.
     */
    fun <D, R, E> recoverSession(request: ApiRequest<D, R, E>): ApiRequest<D, R, E> =
        object : ApiRequest<D, R, E> by pin(request), RecoverableProtectedRequest {
            override val authentication = this@ProtectedRequestAuthentication
        }

    fun <D, R, E> pin(request: ApiRequest<D, R, E>): ApiRequest<D, R, E> = object : ApiRequest<D, R, E> by request {
        // WorkspaceAPIClient must not refresh/replay this operation using replacement credentials.
        override val requiresApiKey = false
        override val additionalHeaders = request.additionalHeaders.filterKeys { !it.equals("Authorization", ignoreCase = true) } +
            ("Authorization" to "Bearer $accessToken")
    }

    override fun toString(): String = "ProtectedRequestAuthentication([REDACTED])"
}

/** Shared transport policy: renew credentials without replaying account-scoped data or IDs. */
interface RecoverableProtectedRequest {
    val authentication: ProtectedRequestAuthentication
}
