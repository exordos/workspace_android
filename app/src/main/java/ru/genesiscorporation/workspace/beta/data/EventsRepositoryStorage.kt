package ru.genesiscorporation.workspace.beta.data

import io.ktor.client.HttpClient
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient

class EventsRepositoryStore(
    private val tokenStore: SecureTokenStore,
    var client: WorkspaceAPIClient
) {
    private val repos = mutableMapOf<String, EventsRepository>()
    @Synchronized
    fun getOrCreate(config: ServerConfig): EventsRepository {
        return repos.getOrPut(config.id) {
            EventsRepository(config.id, config, tokenStore, client)
        }
    }
    @Synchronized
    fun syncWith(servers: List<ServerConfig>) {
        val ids = servers.map { it.id }.toSet()

        servers.forEach { getOrCreate(it) }

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