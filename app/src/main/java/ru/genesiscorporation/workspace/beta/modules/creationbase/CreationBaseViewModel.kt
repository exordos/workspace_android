package ru.genesiscorporation.workspace.beta.modules.creationbase

import androidx.lifecycle.ViewModel
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient

class CreationBaseViewModel(
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {

}