package com.exordos.workspace.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.exordos.workspace.data.crypto.ChatKeyOwner
import com.exordos.workspace.data.remote.ApiRequest
import com.exordos.workspace.data.remote.WorkspaceAPIClient

/** A successful /me binds this credential to this owner. Never persisted or printed. */
class ConfirmedAccountContext private constructor(
    private val server: ServerConfig,
    private val proof: VerifiedOwnerAuthentication,
    private val logoutRevision: Long,
) {
    val serverId: String get() = server.id
    val baseUrl: String get() = server.baseUrl
    val projectUuid: String get() = requireNotNull(server.projectUuid)
    val userUuid: String get() = proof.userUuid
    val verificationRevision: Long get() = proof.revision
    val owner: ChatKeyOwner get() = ChatKeyOwner(baseUrl, projectUuid, userUuid)
    fun serverConfig(): ServerConfig = server.copy()

    fun sameOwner(other: ConfirmedAccountContext?): Boolean = other != null &&
        serverId == other.serverId && owner == other.owner && logoutRevision == other.logoutRevision

    fun matches(other: ConfirmedAccountContext?): Boolean = sameOwner(other) &&
        server == other!!.server && proof.lineage === other.proof.lineage && verificationRevision == other.verificationRevision &&
        proof.authentication.matches(other.proof.authentication)

    fun isCurrent(client: WorkspaceAPIClient, repo: EventsRepository): Boolean = matches(confirmedAccountContext(client, repo))
    fun <D, R, E> pin(request: ApiRequest<D, R, E>): ApiRequest<D, R, E> = proof.authentication.pin(request)

    /** Revoke before the asynchronous logout/purge starts, including observers waiting on IO. */
    fun revoke() { revokeForLogout(server, proof) }

    override fun toString(): String = "ConfirmedAccountContext([REDACTED])"

    companion object {
        private data class Revocation(val revision: Long, val lineage: Any)
        private val lock = Any()
        private val revocations = mutableMapOf<ChatKeyOwner, Revocation>()
        private val mutableInvalidations = MutableStateFlow(0L)
        val invalidations = mutableInvalidations.asStateFlow()

        internal fun revokeForLogout(server: ServerConfig, proof: VerifiedOwnerAuthentication): ChatKeyOwner = synchronized(lock) {
            val owner = ChatKeyOwner(server.baseUrl, requireNotNull(server.projectUuid), proof.userUuid)
            val previous = revocations[owner]
            revocations[owner] = Revocation((previous?.revision ?: 0) + 1, proof.lineage)
            mutableInvalidations.value++
            owner
        }

        /** Pure inputs make the same guard usable by previews, mounted sessions and protected forms. */
        fun from(server: ServerConfig?, selectedServerId: String?, repositoryServerId: String,
                 userUuid: String?, accessToken: String?, proof: VerifiedOwnerAuthentication?): ConfirmedAccountContext? {
            if (server == null || proof == null || userUuid.isNullOrBlank() || server.id != selectedServerId ||
                server.id != repositoryServerId || server.needsToRelogin || server.projectUuid.isNullOrBlank() ||
                proof.capturedServer != server || proof.serverId != server.id || proof.baseUrl != server.baseUrl ||
                proof.userUuid != userUuid || !proof.authentication.matchesAccessToken(accessToken)) return null
            val owner = runCatching { ChatKeyOwner(server.baseUrl, requireNotNull(server.projectUuid), userUuid) }.getOrNull() ?: return null
            return synchronized(lock) {
                val revoked = revocations[owner]
                if (revoked?.lineage === proof.lineage) null else ConfirmedAccountContext(server.copy(), proof, revoked?.revision ?: 0)
            }
        }
    }
}

fun confirmedAccountContext(client: WorkspaceAPIClient, repo: EventsRepository): ConfirmedAccountContext? {
    val user = client.requireUserViewModel()
    return ConfirmedAccountContext.from(user.selectedServer.value, user.selectedServerId.value, repo.serverId,
        repo.currentUser.value?.uuid, user.getTokens(repo.serverId)?.accessToken, repo.verifiedOwnerAuthentication.value)
}
