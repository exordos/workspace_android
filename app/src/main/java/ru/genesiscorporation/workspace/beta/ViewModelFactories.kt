package ru.genesiscorporation.workspace.beta

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.genesiscorporation.workspace.beta.data.ApiKeyRepository
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.modules.chooseserver.ChooseServerViewModel
import ru.genesiscorporation.workspace.beta.modules.login.LoginViewModel
import ru.genesiscorporation.workspace.beta.modules.profile.ProfileViewModel
import ru.genesiscorporation.workspace.beta.modules.topics.TopicsViewModel

class UserViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repo = ApiKeyRepository(appContext) // or ApiKeyRepository(appContext)
        return UserViewModel(repo) as T
    }
}

class WorkspaceViewModelFactory(private val client: WorkspaceAPIClient) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkspaceViewModel(client) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChooseServerViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChooseServerViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChooseServerViewModel(client, userViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class LoginViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(client, userViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(client, userViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatTopicsViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel, private val channelName: String, private val channelStreamId: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TopicsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TopicsViewModel(client, userViewModel, channelName, channelStreamId) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatDialogViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel, private val chatTitle: String, private val chatId: String, private val topicId: String?, private val isDirectMessages: Boolean) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatDialogViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatDialogViewModel(
                client,
                userViewModel,
                chatTitle,
                chatId,
                topicId,
                isDirectMessages
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
class ProfileViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(client, userViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}