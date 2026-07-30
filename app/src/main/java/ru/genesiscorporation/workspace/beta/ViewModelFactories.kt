package ru.genesiscorporation.workspace.beta

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import ru.genesiscorporation.workspace.beta.data.ApiKeyRepository
import ru.genesiscorporation.workspace.beta.data.ConversationStateStore
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.TinkConversationStateStore
import ru.genesiscorporation.workspace.beta.data.TinkRealtimeCursorStore
import ru.genesiscorporation.workspace.beta.data.WorkspaceNotificationSoundController
import ru.genesiscorporation.workspace.beta.data.WorkspaceUiPreferencesRepository
import ru.genesiscorporation.workspace.beta.data.accountAttachmentCacheSizeBytes
import ru.genesiscorporation.workspace.beta.data.clearAccountAttachmentCache
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.push.PushDeviceRegistrationManager
import ru.genesiscorporation.workspace.beta.modules.chatchannels.ChatViewModel
import ru.genesiscorporation.workspace.beta.modules.channelinfo.ChannelInfoViewModel
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.modules.chatuserinfo.ChatUserInfoViewModel
import ru.genesiscorporation.workspace.beta.modules.chooseserver.ChooseServerViewModel
import ru.genesiscorporation.workspace.beta.modules.feed.FeedViewModel
import ru.genesiscorporation.workspace.beta.modules.feed.MessageTimelineKind
import ru.genesiscorporation.workspace.beta.modules.drafts.DraftsViewModel
import ru.genesiscorporation.workspace.beta.modules.login.LoginViewModel
import ru.genesiscorporation.workspace.beta.modules.profile.ProfileViewModel
import ru.genesiscorporation.workspace.beta.modules.topics.TopicsViewModel
import ru.genesiscorporation.workspace.beta.modules.users.UsersViewModel

class UserViewModelFactory(
    private val appContext: Context
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(UserViewModel::class.java)) {
            val conversationStateStore =
                TinkConversationStateStore(appContext)
            val realtimeCursorStore =
                TinkRealtimeCursorStore(appContext)
            @Suppress("UNCHECKED_CAST")
            return UserViewModel(
                repo = ApiKeyRepository(
                    context = appContext,
                    clearAccountLocalData = { ownerKey ->
                        conversationStateStore.clearAccount(ownerKey)
                        realtimeCursorStore.clearAccount(ownerKey)
                    },
                ),
                conversationStateStore = conversationStateStore,
                uiPreferencesRepository =
                    WorkspaceUiPreferencesRepository(appContext),
                realtimeCursorStore = realtimeCursorStore,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class WorkspaceNetworkViewModelFactory(
    private val userViewModel: UserViewModel,
    private val appContext: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceNetworkViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkspaceNetworkViewModel(userViewModel, appContext) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class WorkspaceViewModelFactory(
    private val client: WorkspaceAPIClient,
    private val repo: EventsRepository,
    private val pushDeviceRegistrationManager: PushDeviceRegistrationManager,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WorkspaceViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WorkspaceViewModel(
                client,
                repo,
                pushDeviceRegistrationManager,
            ) as T
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

class ChatViewModelFactory(private val client: WorkspaceAPIClient,
                           private val userViewModel: UserViewModel,
                           private val repo: EventsRepository,
                           private val conversationStateStore: ConversationStateStore) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChatViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChatViewModel(
                client,
                userViewModel,
                repo,
                conversationStateStore,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class FeedViewModelFactory(
    private val client: WorkspaceAPIClient,
    private val userViewModel: UserViewModel,
    private val kind: MessageTimelineKind = MessageTimelineKind.FEED,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FeedViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FeedViewModel(client, userViewModel, kind) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

class DraftsViewModelFactory(
    private val client: WorkspaceAPIClient,
    private val userViewModel: UserViewModel,
    private val conversationStateStore: ConversationStateStore,
    private val context: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DraftsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DraftsViewModel(
                client,
                userViewModel,
                conversationStateStore,
                context,
            ) as T
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

class ChatDialogViewModelFactory(
    private val client: WorkspaceAPIClient,
    private val userViewModel: UserViewModel,
    private val chatTitle: String,
    private val chatId: String,
    private val topicName: String?,
    private val topicUuid: String,
    private val isDirectMessages: Boolean,
    private val repo: EventsRepository,
    private val userId: Int?,
    private val focusProviderMessageId: String? = null,
    private val focusMessageUuid: String? = null,
    private val beginForwardMessageUuid: String? = null,
    private val draftStorageSlot: String? = null,
    private val conversationStateStore: ConversationStateStore,
) : ViewModelProvider.Factory {
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
                userId,
                focusProviderMessageId,
                focusMessageUuid,
                beginForwardMessageUuid,
                draftStorageSlot,
                conversationStateStore,
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
class ProfileViewModelFactory(
    private val userViewModel: UserViewModel,
    private val client: WorkspaceAPIClient,
    private val pushDeviceRegistrationManager: PushDeviceRegistrationManager,
    private val appContext: Context,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ProfileViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ProfileViewModel(
                userViewModel,
                client,
                pushDeviceRegistrationManager,
                activateNotificationSound = { sound ->
                    WorkspaceNotificationSoundController.activate(appContext, sound)
                },
                readAttachmentCacheSizeBytes = { ownerKey ->
                    accountAttachmentCacheSizeBytes(appContext.cacheDir, ownerKey)
                },
                deleteAttachmentCache = { ownerKey ->
                    clearAccountAttachmentCache(appContext.cacheDir, ownerKey)
                },
            ) as T
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

class ChannelInfoViewModelFactory(
    private val client: WorkspaceAPIClient,
    private val streamUuid: String,
    private val repo: EventsRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ChannelInfoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ChannelInfoViewModel(streamUuid, client, repo) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
