package ru.genesiscorporation.workspace.beta.modules.channelinfo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamNotificationsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest

class ChannelInfoViewModel(
    val streamUuid: String,
    val client: WorkspaceAPIClient,
    val repo: EventsRepository,
) : ViewModel() {
    private val bindings = MutableStateFlow<List<StreamBindingResponseData>?>(null)

    val stream: StateFlow<Stream?> = repo.streams
        .map { streams -> streams.firstOrNull { it.uuid == streamUuid } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val members: StateFlow<List<ChannelMember>> = combine(
        repo.users,
        bindings,
    ) { users, loadedBindings ->
        resolveChannelMembers(streamUuid, loadedBindings, users)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isUsingMockMembers: StateFlow<Boolean> = bindings
        .map { loadedBindings ->
            loadedBindings != null && loadedBindings.none { it.streamUuid == streamUuid }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        if (repo.streams.value.none { it.uuid == streamUuid }) {
            viewModelScope.launch {
                when (val response = client.performRequest(StreamsRequest())) {
                    is ApiResult.Success -> repo.setInitialStreams(response.value)
                    is ApiResult.Error -> Unit
                }
            }
        }
        if (repo.users.value.isEmpty()) {
            viewModelScope.launch {
                when (val response = client.performRequest(UsersRequest())) {
                    is ApiResult.Success -> repo.setInitialUsers(response.value)
                    is ApiResult.Error -> Unit
                }
            }
        }
        viewModelScope.launch {
            bindings.value = when (val response = client.performRequest(StreamBindingsRequest())) {
                is ApiResult.Success -> response.value
                is ApiResult.Error -> emptyList()
            }
        }
    }

    fun toggleMuted() {
        val currentStream = stream.value ?: return
        val targetMode = if (currentStream.notificationMode == "muted") {
            "all_messages"
        } else {
            "muted"
        }
        viewModelScope.launch {
            when (
                val response = client.performRequest(
                    StreamNotificationsRequest(streamUuid, targetMode),
                )
            ) {
                is ApiResult.Success -> repo.updateStream(response.value)
                is ApiResult.Error -> Unit
            }
        }
    }
}

data class ChannelMember(
    val user: UserResponseData,
    val role: String,
    val isMockMembership: Boolean,
)

internal fun resolveChannelMembers(
    streamUuid: String,
    bindings: List<StreamBindingResponseData>?,
    users: List<UserResponseData>,
): List<ChannelMember> {
    if (bindings == null) return emptyList()

    val streamBindings = bindings.filter { it.streamUuid == streamUuid }
    if (streamBindings.isEmpty()) {
        return users.take(5).mapIndexed { index, user ->
            ChannelMember(
                user = user,
                role = if (index == 0) "owner" else "member",
                isMockMembership = true,
            )
        }
    }

    val bindingByUser = streamBindings.associateBy { it.userUuid }
    return users
        .filter { bindingByUser.containsKey(it.uuid) }
        .map { user ->
            ChannelMember(
                user = user,
                role = bindingByUser.getValue(user.uuid).role,
                isMockMembership = false,
            )
        }
        .sortedWith(
            compareByDescending<ChannelMember> { it.role == "owner" }
                .thenBy { it.user.displayableName().lowercase() },
        )
}
