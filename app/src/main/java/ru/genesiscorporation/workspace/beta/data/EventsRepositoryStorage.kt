package ru.genesiscorporation.workspace.beta.data

import io.ktor.client.HttpClient
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient

class EventsRepositoryStorage(
    val client: WorkspaceAPIClient
) {
    var storage: MutableMap<String, EventsRepository> = mutableMapOf()
    var userViewModel: UserViewModel? = null

    fun getCurrentEventsRepository(): EventsRepository? {
        if (storage.isEmpty()) {
            return null
        } else {
            val baseUrl = userViewModel?.baseUrl?.value
            if (baseUrl != null) {
                return storage[baseUrl]
            } else {
                return null
            }
        }
    }

    fun addEventsRepository() {
        val baseUrl = userViewModel?.baseUrl?.value
        if (baseUrl != null) {
            val eventsRepository = EventsRepository()
            eventsRepository.client = client
            storage[baseUrl] = eventsRepository
        }
    }
}