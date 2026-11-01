package com.exordos.workspace

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CoroutineScope
import com.exordos.workspace.data.ServerRepository
import com.exordos.workspace.data.EventsRepository
import com.exordos.workspace.data.EventsRepositoryStore
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.modules.addfolder.AddFolderViewModel
import com.exordos.workspace.modules.adduserstostream.AddUsersToStreamView
import com.exordos.workspace.modules.adduserstostream.AddUsersToStreamViewModel
import com.exordos.workspace.modules.calendar.CalendarViewModel
import com.exordos.workspace.modules.chatchannels.ChatViewModel
import com.exordos.workspace.modules.chatdialog.AttachmentStorage
import com.exordos.workspace.modules.chatdialog.ChatDialogViewModel
import com.exordos.workspace.modules.chatuserinfo.ChatUserInfoViewModel
import com.exordos.workspace.modules.chooseserver.ChooseServerViewModel
import com.exordos.workspace.modules.createdirectstream.CreateDirectStreamViewModel
import com.exordos.workspace.modules.createstream.CreateStreamViewModel
import com.exordos.workspace.modules.creationbase.CreationBaseViewModel
import com.exordos.workspace.modules.foldersettings.FolderSettingsViewModel
import com.exordos.workspace.modules.home.HomeViewModel
import com.exordos.workspace.modules.homedrafts.HomeDraftsViewModel
import com.exordos.workspace.modules.homeinbounds.HomeInboundsViewModel
import com.exordos.workspace.modules.homementions.HomeMentionsViewModel
import com.exordos.workspace.modules.login.LoginViewModel
import com.exordos.workspace.modules.mail.MailViewModel
import com.exordos.workspace.modules.otp.OtpViewModel
import com.exordos.workspace.modules.ownusersettings.OwnUserSettingsViewModel
import com.exordos.workspace.modules.profile.ProfileViewModel
import com.exordos.workspace.modules.projects.ProjectsViewModel
import com.exordos.workspace.modules.streaminfo.StreamInfoViewModel
import com.exordos.workspace.modules.topics.TopicsViewModel
import com.exordos.workspace.modules.users.UsersViewModel
import com.exordos.workspace.modules.visualsettings.VisualSettingsViewModel

class UserViewModelFactory(
    private val repo: ServerRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return UserViewModel(repo) as T
    }
}

class WorkspaceViewModelFactory(private val client: WorkspaceAPIClient, val eventsRepositoryStore: EventsRepositoryStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkspaceViewModel(client, eventsRepositoryStore) as T
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

class LoginViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel, val isFirstOrganization: Boolean) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(LoginViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(client, userViewModel, isFirstOrganization) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class OtpViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel, val login: String, val password: String, val isFirstOrganization: Boolean) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OtpViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OtpViewModel(client, userViewModel, login, password, isFirstOrganization) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ProjectsViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel, val isFirstOrganization: Boolean) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProjectsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProjectsViewModel(client, userViewModel, isFirstOrganization) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}


class CreationBaseViewModelFactory(private val eventsRepositoryStore: EventsRepositoryStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreationBaseViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CreationBaseViewModel(eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class CreateStreamViewModelFactory(private val client: WorkspaceAPIClient, private val eventsRepositoryStore: EventsRepositoryStore, private val context: android.content.Context? = null) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateStreamViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CreateStreamViewModel(client, eventsRepositoryStore, context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class FolderSettingsViewModelFactory(private val client: WorkspaceAPIClient, private val eventsRepositoryStore: EventsRepositoryStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FolderSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FolderSettingsViewModel(client, eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class AddFolderViewModelFactory(private val client: WorkspaceAPIClient, private val eventsRepositoryStore: EventsRepositoryStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddFolderViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddFolderViewModel(client, eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class CreateDirectStreamViewModelFactory(private val client: WorkspaceAPIClient, private val eventsRepositoryStore: EventsRepositoryStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CreateDirectStreamViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CreateDirectStreamViewModel(client, eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatViewModelFactory(private val client: WorkspaceAPIClient,
                           private val userViewModel: UserViewModel,
                           private  val eventsRepositoryStore: EventsRepositoryStore,
                           private  val pendingDeepLink: String?,
                           private  val onDeepLinkHandled: () -> Unit) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(client, userViewModel, eventsRepositoryStore, pendingDeepLink, onDeepLinkHandled) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatTopicsViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel, private val channelName: String, private val channelStreamId: String, private  val eventsRepositoryStore: EventsRepositoryStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(TopicsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return TopicsViewModel(client, userViewModel, channelName, channelStreamId, eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatDialogViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel, private val chatTitle: String, private val chatId: String, private val topicName: String?, private val topicUuid: String, private val isDirectMessages: Boolean, private  val eventsRepositoryStore: EventsRepositoryStore, val userId: Int?, private val storage: AttachmentStorage, private val firstUnreadMessageUuid: String? = null) : ViewModelProvider.Factory {
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
                eventsRepositoryStore,
                userId,
                storage,
                firstUnreadMessageUuid
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class StreamInfoViewModelFactory(private val client: WorkspaceAPIClient, private val streamUuid: String, private val topicUuid: String, private  val eventsRepositoryStore: EventsRepositoryStore, private val context: android.content.Context? = null) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StreamInfoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StreamInfoViewModel(
                streamUuid,
                topicUuid,
                client,
                eventsRepositoryStore,
                context,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
class ProfileViewModelFactory(private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel, private val eventsRepositoryStore: EventsRepositoryStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(client, userViewModel, eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class OwnUserSettingsViewModelFactory(private val client: WorkspaceAPIClient, private val eventsRepositoryStore: EventsRepositoryStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(OwnUserSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return OwnUserSettingsViewModel(client, eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class VisualSettingsViewModelFactory(private val userViewModel: UserViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(VisualSettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return VisualSettingsViewModel(userViewModel) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class ChatUserInfoViewModelFactory(private val client: WorkspaceAPIClient, val userName: String, val userId: String, val avatarUrl: String, val email: String, val eventsRepositoryStore: EventsRepositoryStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatUserInfoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatUserInfoViewModel(userName, userId, avatarUrl, email, client, eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class AddUsersToStreamViewModelFactory(private val client: WorkspaceAPIClient, private val streamUuid: String, private  val eventsRepositoryStore: EventsRepositoryStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(AddUsersToStreamViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return AddUsersToStreamViewModel(
                streamUuid,
                client,
                eventsRepositoryStore
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class MailViewModelFactory(private val eventsRepositoryStore: EventsRepositoryStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MailViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MailViewModel(eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class CalendarViewModelFactory(private val eventsRepositoryStore: EventsRepositoryStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CalendarViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CalendarViewModel(eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class HomeViewModelFactory(private val eventsRepositoryStore: EventsRepositoryStore, private val client: WorkspaceAPIClient, private val userViewModel: UserViewModel) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(client, userViewModel, eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class HomeMentionsViewModelFactory(private val eventsRepositoryStore: EventsRepositoryStore, private val client: WorkspaceAPIClient) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeMentionsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeMentionsViewModel(client, eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class HomeInboundsViewModelFactory(private val eventsRepositoryStore: EventsRepositoryStore, private val client: WorkspaceAPIClient) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeInboundsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeInboundsViewModel(client, eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class HomeDraftsViewModelFactory(private val eventsRepositoryStore: EventsRepositoryStore, private val client: WorkspaceAPIClient) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeDraftsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeDraftsViewModel(client, eventsRepositoryStore) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
