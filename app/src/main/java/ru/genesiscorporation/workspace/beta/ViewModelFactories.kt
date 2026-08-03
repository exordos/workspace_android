package ru.genesiscorporation.workspace.beta

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import ru.genesiscorporation.workspace.beta.data.ApiKeyRepository
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.modules.addfolder.AddFolderViewModel
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.modules.chatuserinfo.ChatUserInfoViewModel
import ru.genesiscorporation.workspace.beta.modules.chooseserver.ChooseServerViewModel
import ru.genesiscorporation.workspace.beta.modules.createdirectstream.CreateDirectStreamViewModel
import ru.genesiscorporation.workspace.beta.modules.createstream.CreateStreamViewModel
import ru.genesiscorporation.workspace.beta.modules.creationbase.CreationBaseViewModel
import ru.genesiscorporation.workspace.beta.modules.foldersettings.FolderSettingsViewModel
import ru.genesiscorporation.workspace.beta.modules.login.LoginViewModel
import ru.genesiscorporation.workspace.beta.modules.otp.OtpViewModel
import ru.genesiscorporation.workspace.beta.modules.ownusersettings.OwnUserSettingsViewModel
import ru.genesiscorporation.workspace.beta.modules.profile.ProfileViewModel
import ru.genesiscorporation.workspace.beta.modules.topics.TopicsViewModel
import ru.genesiscorporation.workspace.beta.modules.users.UsersViewModel

class UserViewModelFactory(
    private val appContext: Context,
    private val scope: CoroutineScope
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val repo = ApiKeyRepository(appContext, scope)
        return UserViewModel(repo) as T
    }
}

class WorkspaceViewModelFactory(private val client: WorkspaceAPIClient, val repo: EventsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkspaceViewModel(client, repo) as T
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

class OtpViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel, val login: String, val password: String) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OtpViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OtpViewModel(client, userViewModel, login, password) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


class CreationBaseViewModelFactory(private val repo: EventsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreationBaseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CreationBaseViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class CreateStreamViewModelFactory(private val client: WorkspaceAPIClient, private val repo: EventsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateStreamViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CreateStreamViewModel(client, repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class FolderSettingsViewModelFactory(private val repo: EventsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FolderSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FolderSettingsViewModel(repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class AddFolderViewModelFactory(private val client: WorkspaceAPIClient, private val repo: EventsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddFolderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddFolderViewModel(client, repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class CreateDirectStreamViewModelFactory(private val client: WorkspaceAPIClient, private val repo: EventsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateDirectStreamViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CreateDirectStreamViewModel(client, repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatViewModelFactory(private val client: WorkspaceAPIClient,
                           private val userViewModel: UserViewModel,
                           private  val repo: EventsRepository,
                           private  val pendingDeepLink: String?,
                           private  val onDeepLinkHandled: () -> Unit) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(client, userViewModel, repo, pendingDeepLink, onDeepLinkHandled) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatTopicsViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel, private val channelName: String, private val channelStreamId: String, private  val repo: EventsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TopicsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TopicsViewModel(client, userViewModel, channelName, channelStreamId, repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatDialogViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel, private val chatTitle: String, private val chatId: String, private val topicName: String?, private val topicUuid: String, private val isDirectMessages: Boolean, private  val repo: EventsRepository, val userId: Int?) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatDialogViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatDialogViewModel(
                client,
                userViewModel,
                chatTitle,
                chatId,
                topicName,
                topicUuid,
                isDirectMessages,
                repo,
                userId
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
class ProfileViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel, private val repo: EventsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(client, userViewModel, repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class OwnUserSettingsViewModelFactory(private val client: WorkspaceAPIClient, private val repo: EventsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OwnUserSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OwnUserSettingsViewModel(client, repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class UsersViewModelFactory(private val client: WorkspaceAPIClient) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UsersViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return UsersViewModel(client) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatUserInfoViewModelFactory(private val client: WorkspaceAPIClient, val userName: String, val userId: String, val avatarUrl: String, val email: String, val repo: EventsRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatUserInfoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatUserInfoViewModel(userName, userId, avatarUrl, email, client, repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}