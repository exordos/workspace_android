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
import kotlinx.coroutines.sync.Mutex
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamNotificationsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.AddStreamMembersRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteStreamBindingRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeleteStreamRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateStreamBindingRoleRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateStreamRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest
import java.util.concurrent.atomic.AtomicLong

internal object MemberActionKind {
    const val ADD = "add"
    const val REMOVE = "remove"
    const val ROLE = "role"
}

data class MemberActionResult(
    val requestId: Long,
    val kind: String,
    val userUuid: String?,
    val success: Boolean,
)

class ChannelInfoViewModel(
    val streamUuid: String,
    val client: WorkspaceAPIClient,
    val repo: EventsRepository,
) : ViewModel() {
    private val bindings = MutableStateFlow<List<StreamBindingResponseData>?>(
        repo.streamBindings.value
            .filter { it.streamUuid == streamUuid }
            .takeIf(List<StreamBindingResponseData>::isNotEmpty),
    )
    private val bindingsAuthoritative = MutableStateFlow(false)
    private val _muteInProgress = MutableStateFlow(false)
    val muteInProgress: StateFlow<Boolean> = _muteInProgress
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError
    private val _channelActionInProgress = MutableStateFlow(false)
    val channelActionInProgress: StateFlow<Boolean> = _channelActionInProgress
    private val _deletedStream = MutableStateFlow(false)
    val deletedStream: StateFlow<Boolean> = _deletedStream
    private val _memberActionInProgress = MutableStateFlow(false)
    val memberActionInProgress: StateFlow<Boolean> = _memberActionInProgress
    private val _memberLoadError = MutableStateFlow<String?>(null)
    val memberLoadError: StateFlow<String?> = _memberLoadError
    private val _leftStream = MutableStateFlow(false)
    val leftStream: StateFlow<Boolean> = _leftStream
    private val _lastMemberActionResult =
        MutableStateFlow<MemberActionResult?>(null)
    val lastMemberActionResult: StateFlow<MemberActionResult?> =
        _lastMemberActionResult
    private val memberActionMutex = Mutex()
    private val nextMemberActionRequestId = AtomicLong()

    val stream: StateFlow<Stream?> = repo.streams
        .map { streams -> streams.firstOrNull { it.uuid == streamUuid } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val members: StateFlow<List<ChannelMember>> = combine(
        repo.users,
        bindings,
    ) { users, loadedBindings ->
        resolveChannelMembers(streamUuid, loadedBindings, users)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val availableUsers: StateFlow<List<UserResponseData>> = combine(
        repo.users,
        bindings,
        bindingsAuthoritative,
    ) { users, loadedBindings, authoritative ->
        if (loadedBindings == null || !authoritative) {
            return@combine emptyList()
        }
        val memberUserUuids = loadedBindings
            .asSequence()
            .filter { it.streamUuid == streamUuid }
            .map { it.userUuid }
            .toSet()
        users
            .filterNot { it.uuid in memberUserUuids }
            .sortedBy { it.displayableName().lowercase() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
            loadBindings()
        }
    }

    fun setNotificationMode(notificationMode: String) {
        if (_muteInProgress.value) return
        if (notificationMode !in STREAM_NOTIFICATION_MODES) return
        val currentStream = stream.value ?: return
        if (currentStream.notificationMode == notificationMode) return
        viewModelScope.launch {
            _muteInProgress.value = true
            _actionError.value = null
            when (
                val response = client.performRequest(
                    StreamNotificationsRequest(streamUuid, notificationMode),
                )
            ) {
                is ApiResult.Success -> repo.updateStream(response.value)
                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: "Не удалось изменить уведомления"
                }
            }
            _muteInProgress.value = false
        }
    }

    fun updateChannelDetails(name: String, description: String) {
        if (_channelActionInProgress.value || !canManageCurrentChannel()) return
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) {
            _actionError.value = "Название канала не может быть пустым"
            return
        }
        val normalizedDescription = description.trim()
        val currentStream = stream.value ?: return
        if (
            currentStream.name == normalizedName &&
            currentStream.description == normalizedDescription
        ) {
            return
        }
        viewModelScope.launch {
            runChannelUpdate(
                UpdateStreamRequest(
                    streamUuid = streamUuid,
                    name = normalizedName,
                    description = normalizedDescription,
                ),
                fallbackError = "Не удалось сохранить канал",
            )
        }
    }

    internal fun updateChannelVisibility(visibility: ChannelVisibility) {
        if (_channelActionInProgress.value || !canManageCurrentChannel()) return
        val currentStream = stream.value ?: return
        val flags = visibility.flags()
        if (
            currentStream.inviteOnly == flags.inviteOnly &&
            currentStream.isPrivate == flags.isPrivate
        ) {
            return
        }
        viewModelScope.launch {
            runChannelUpdate(
                UpdateStreamRequest(
                    streamUuid = streamUuid,
                    inviteOnly = flags.inviteOnly,
                    isPrivate = flags.isPrivate,
                ),
                fallbackError = "Не удалось изменить тип канала",
            )
        }
    }

    fun deleteChannel() {
        val currentStream = stream.value ?: return
        if (
            _channelActionInProgress.value ||
            !canDeleteChannel(
                currentUserUuid = client.userViewModel.userId.value,
                ownerUuid = currentStream.owner,
            )
        ) {
            return
        }
        viewModelScope.launch {
            _channelActionInProgress.value = true
            _actionError.value = null
            when (
                val response = client.performRequest(DeleteStreamRequest(streamUuid))
            ) {
                is ApiResult.Success -> {
                    repo.removeStream(streamUuid)
                    _deletedStream.value = true
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: "Не удалось удалить канал"
                }
            }
            _channelActionInProgress.value = false
        }
    }

    private suspend fun runChannelUpdate(
        request: UpdateStreamRequest,
        fallbackError: String,
    ) {
        _channelActionInProgress.value = true
        _actionError.value = null
        when (val response = client.performRequest(request)) {
            is ApiResult.Success -> repo.updateStream(response.value)
            is ApiResult.Error -> {
                _actionError.value = response.error.message ?: fallbackError
            }
        }
        _channelActionInProgress.value = false
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun retryMembers() {
        if (_memberActionInProgress.value) return
        viewModelScope.launch { loadBindings() }
    }

    fun canManageCurrentChannel(): Boolean =
        canManageChannel(stream.value?.role)

    fun canManageMember(member: ChannelMember): Boolean =
        canManageChannelMember(
            currentUserRole = stream.value?.role,
            currentUserUuid = client.userViewModel.userId.value,
            memberUserUuid = member.user.uuid,
            memberRole = member.role,
            bindingsAuthoritative = bindingsAuthoritative.value,
        )

    fun canRemoveMember(member: ChannelMember): Boolean =
        canRemoveChannelMember(
            currentUserRole = stream.value?.role,
            currentUserUuid = client.userViewModel.userId.value,
            memberUserUuid = member.user.uuid,
            memberRole = member.role,
            bindingsAuthoritative = bindingsAuthoritative.value,
        )

    fun addMembers(userUuids: Collection<String>): Long = launchMemberAction(
        kind = MemberActionKind.ADD,
        userUuid = null,
    ) {
        addMembersInternal(userUuids)
    }

    private suspend fun addMembersInternal(
        userUuids: Collection<String>,
    ): Boolean {
        if (!canManageCurrentChannel()) {
            _actionError.value = "Недостаточно прав для добавления участников"
            return false
        }
        if (!memberActionMutex.tryLock()) return false
        val loadedBindings = bindings.value
        if (loadedBindings == null || !bindingsAuthoritative.value) {
            _actionError.value = "Сначала загрузите актуальный список участников"
            memberActionMutex.unlock()
            return false
        }
        val normalizedUuids = userUuids
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
            .filterNot { candidate ->
                loadedBindings
                    .any { it.streamUuid == streamUuid && it.userUuid == candidate }
            }
        if (normalizedUuids.isEmpty()) {
            _actionError.value = "Выберите хотя бы одного нового участника"
            memberActionMutex.unlock()
            return false
        }
        _memberActionInProgress.value = true
        _actionError.value = null
        return try {
            when (
                val response = client.performRequest(
                    AddStreamMembersRequest(streamUuid, normalizedUuids),
                )
            ) {
                is ApiResult.Success -> {
                    val updatedBindings = (
                        bindings.value
                            .orEmpty()
                            .filterNot { existing ->
                                response.value.any { it.uuid == existing.uuid }
                            } + response.value
                    )
                    repo.replaceStreamBindings(streamUuid, updatedBindings)
                    bindings.value = updatedBindings
                    true
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: "Не удалось добавить участников"
                    false
                }
            }
        } finally {
            _memberActionInProgress.value = false
            memberActionMutex.unlock()
        }
    }

    fun removeMember(member: ChannelMember): Long = launchMemberAction(
        kind = MemberActionKind.REMOVE,
        userUuid = member.user.uuid,
    ) {
        removeMemberInternal(member)
    }

    private suspend fun removeMemberInternal(
        member: ChannelMember,
    ): Boolean {
        if (!canRemoveMember(member)) {
            _actionError.value = "Недостаточно прав для удаления этого участника"
            return false
        }
        if (!memberActionMutex.tryLock()) {
            return false
        }
        _memberActionInProgress.value = true
        _actionError.value = null
        return try {
            when (
                val response = client.performRequest(
                    DeleteStreamBindingRequest(member.bindingUuid),
                )
            ) {
                is ApiResult.Success -> {
                    val updatedBindings = bindings.value
                        .orEmpty()
                        .filterNot { it.uuid == member.bindingUuid }
                    repo.replaceStreamBindings(streamUuid, updatedBindings)
                    bindings.value = updatedBindings
                    if (member.user.uuid == client.userViewModel.userId.value) {
                        repo.removeStream(streamUuid)
                        _leftStream.value = true
                    }
                    true
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: "Не удалось удалить участника"
                    false
                }
            }
        } finally {
            _memberActionInProgress.value = false
            memberActionMutex.unlock()
        }
    }

    fun updateMemberRole(member: ChannelMember, role: String): Long =
        launchMemberAction(
            kind = MemberActionKind.ROLE,
            userUuid = member.user.uuid,
        ) {
            updateMemberRoleInternal(member, role)
        }

    private suspend fun updateMemberRoleInternal(
        member: ChannelMember,
        role: String,
    ): Boolean {
        if (!canManageMember(member) || !isEditableChannelMemberRole(role)) {
            _actionError.value = "Недостаточно прав для изменения роли"
            return false
        }
        if (member.role == role) return true
        if (!memberActionMutex.tryLock()) return false
        _memberActionInProgress.value = true
        _actionError.value = null
        return try {
            when (
                val response = client.performRequest(
                    UpdateStreamBindingRoleRequest(member.bindingUuid, role),
                )
            ) {
                is ApiResult.Success -> {
                    val updatedBindings = bindings.value
                        .orEmpty()
                        .map { existing ->
                            if (existing.uuid == member.bindingUuid) {
                                response.value
                            } else {
                                existing
                            }
                        }
                    repo.replaceStreamBindings(streamUuid, updatedBindings)
                    bindings.value = updatedBindings
                    true
                }

                is ApiResult.Error -> {
                    _actionError.value = response.error.message
                        ?: "Не удалось изменить роль"
                    false
                }
            }
        } finally {
            _memberActionInProgress.value = false
            memberActionMutex.unlock()
        }
    }

    private fun launchMemberAction(
        kind: String,
        userUuid: String?,
        action: suspend () -> Boolean,
    ): Long {
        val requestId = nextMemberActionRequestId.incrementAndGet()
        viewModelScope.launch {
            _lastMemberActionResult.value = MemberActionResult(
                requestId = requestId,
                kind = kind,
                userUuid = userUuid,
                success = action(),
            )
        }
        return requestId
    }

    private suspend fun loadBindings() {
        if (!memberActionMutex.tryLock()) return
        _memberActionInProgress.value = true
        try {
            when (
                val response = client.performRequest(StreamBindingsRequest(streamUuid))
            ) {
                is ApiResult.Success -> {
                    _memberLoadError.value = null
                    repo.replaceStreamBindings(streamUuid, response.value)
                    bindings.value = response.value
                    bindingsAuthoritative.value = true
                }

                is ApiResult.Error -> {
                    bindingsAuthoritative.value = false
                    _memberLoadError.value = response.error.message
                        ?: "Не удалось загрузить участников"
                }
            }
        } finally {
            _memberActionInProgress.value = false
            memberActionMutex.unlock()
        }
    }
}

data class ChannelMember(
    val user: UserResponseData,
    val role: String,
    val bindingUuid: String,
)

internal fun resolveChannelMembers(
    streamUuid: String,
    bindings: List<StreamBindingResponseData>?,
    users: List<UserResponseData>,
): List<ChannelMember> {
    if (bindings == null) return emptyList()

    val streamBindings = bindings.filter { it.streamUuid == streamUuid }
    if (streamBindings.isEmpty()) return emptyList()

    val bindingByUser = streamBindings.associateBy { it.userUuid }
    return users
        .filter { bindingByUser.containsKey(it.uuid) }
        .map { user ->
            ChannelMember(
                user = user,
                role = bindingByUser.getValue(user.uuid).role,
                bindingUuid = bindingByUser.getValue(user.uuid).uuid,
            )
        }
        .sortedWith(
            compareByDescending<ChannelMember> { it.role == "owner" }
                .thenBy { it.user.displayableName().lowercase() },
        )
}

private val STREAM_NOTIFICATION_MODES = setOf(
    "mentions_only",
    "muted",
    "all_messages",
)
