package ru.genesiscorporation.workspace.beta.modules.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.push.PushDeviceRegistrationManager

class ProfileViewModel(
    val userViewModel: UserViewModel,
    private val pushDeviceRegistrationManager: PushDeviceRegistrationManager,
): ViewModel() {

    fun logout() {
        viewModelScope.launch {
            pushDeviceRegistrationManager.deleteRegistration()
            userViewModel.clearAllAndWait()
        }
    }
}
