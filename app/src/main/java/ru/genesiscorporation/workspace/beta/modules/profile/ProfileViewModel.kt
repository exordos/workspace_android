package ru.genesiscorporation.workspace.beta.modules.profile

import androidx.lifecycle.ViewModel
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient

class ProfileViewModel(
    val client: WorkspaceAPIClient,
    val userViewModel: UserViewModel,
    private val repo: EventsRepository
): ViewModel() {

    fun currentPushToken(): String? {
        return repo.pushId
    }
    fun logout() {
        userViewModel.clearAll()
    }
}
