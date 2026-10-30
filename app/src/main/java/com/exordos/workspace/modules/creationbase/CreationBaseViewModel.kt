package com.exordos.workspace.modules.creationbase

import androidx.lifecycle.ViewModel
import com.exordos.workspace.UserViewModel
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.remote.WorkspaceAPIClient

class CreationBaseViewModel(
    val eventsRepositoryStore: EventsRepositoryStore
): ViewModel() {

}
