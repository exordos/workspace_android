package ru.genesiscorporation.workspace.beta.data

import io.ktor.client.HttpClient
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient

class EventsRepositoryStore(
    private val tokenStore: SecureTokenStore,
    var client: WorkspaceAPIClient
) {
    private val repos = mutableMapOf<String, EventsRepository>()
    var onPushMessagesKnown: (String, String?, List<ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse>) -> Unit = { _, _, _ -> }
    var onPushMessageRemoved: (String, String?, String) -> Unit = { _, _, _ -> }
    var onPushRead: (String, String?, List<String>) -> Unit = { _, _, _ -> }
    var onPushRefreshNeeded: () -> Unit = {}

    @Synchronized
    fun getOrCreate(config: ServerConfig): EventsRepository {
        repos[config.id]?.let { existing ->
            if (existing.matchesServerConfiguration(config)) return existing
            existing.close()
        }
        return EventsRepository(config.id, config.copy(), tokenStore, client).also { repo ->
            repo.onPushMessagesKnown = { onPushMessagesKnown(config.id, repo.currentUser.value?.uuid, it) }
            repo.onPushMessageRemoved = { onPushMessageRemoved(config.id, repo.currentUser.value?.uuid, it) }
            repo.onPushRead = { onPushRead(config.id, repo.currentUser.value?.uuid, it) }
            repo.onPushRefreshNeeded = { onPushRefreshNeeded() }
            repos[config.id] = repo
        }
    }

    @Synchronized
    fun getOrCreateForId(serverId: String, configs: List<ServerConfig>): EventsRepository? {
        val config = configs.find { it.id == serverId } ?: return null
        return getOrCreate(config)
    }

    @Synchronized
    fun startAllRepos() {
        repos.keys.forEach {
            repos[it]?.loadServerSettings()
        }
    }

    @Synchronized
    fun syncWith(servers: List<ServerConfig>) {
        val ids = servers.map { it.id }.toSet()

        servers.forEach {
            getOrCreate(it)
        }

        repos.keys
            .filter { it !in ids }
            .forEach { id -> repos.remove(id)?.close() }
    }
    @Synchronized
    fun get(serverId: String): EventsRepository? = repos[serverId]

    @Synchronized
    fun clear() {
        repos.values.forEach { it.close() }
        repos.clear()
    }
}
