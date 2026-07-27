package ru.genesiscorporation.workspace.beta.modules.chatuserinfo

import androidx.annotation.DrawableRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.Stream
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamsRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.data.remote.dto.UsersRequest

class ChatUserInfoViewModel(
    val userName: String,
    val userId: String,
    val avatarUrl: String,
    val email: String,
    val client: WorkspaceAPIClient,
    val repo: EventsRepository,
) : ViewModel() {
    private val bindings = MutableStateFlow<List<StreamBindingResponseData>?>(null)

    val profile: StateFlow<UserResponseData?> = repo.users
        .map { users -> users.firstOrNull { it.uuid == userId } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val channels: StateFlow<List<Stream>> = combine(
        repo.streams,
        bindings,
    ) { streams, loadedBindings ->
        resolveSharedChannels(userId, streams, loadedBindings)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val isUsingMockChannels: StateFlow<Boolean> = bindings
        .map { loadedBindings ->
            loadedBindings != null && loadedBindings.none { it.userUuid == userId }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        if (repo.streams.value.isEmpty()) {
            viewModelScope.launch {
                when (val response = client.performRequest(StreamsRequest())) {
                    is ApiResult.Success -> repo.setInitialStreams(response.value)
                    is ApiResult.Error -> Unit
                }
            }
        }
        if (repo.users.value.none { it.uuid == userId }) {
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

    fun profileFields(): List<ProfileField> {
        val user = profile.value
        return listOf(
            ProfileField(
                title = "Статус",
                value = user?.statusText?.takeIf(String::isNotBlank) ?: "Работаю",
                icon = R.drawable.ic_figma_profile_status,
                isMock = user?.statusText.isNullOrBlank(),
            ),
            ProfileField(
                title = "Телефон",
                value = "+7 999 123-45-67",
                icon = R.drawable.ic_figma_profile_phone,
                isMock = true,
            ),
            ProfileField(
                title = "Email",
                value = user?.email?.takeIf(String::isNotBlank)
                    ?: email.takeIf(String::isNotBlank)
                    ?: "user@example.com",
                icon = R.drawable.ic_figma_profile_email,
                isMock = user?.email.isNullOrBlank() && email.isBlank(),
            ),
            ProfileField(
                title = "ID пользователя",
                value = userId,
                icon = R.drawable.ic_userid,
            ),
            ProfileField(
                title = "Местное время",
                value = "12:49",
                icon = R.drawable.ic_figma_profile_schedule,
                isMock = true,
            ),
            ProfileField(
                title = "Команда > Должность",
                value = "Platform > Ui/Ux designer",
                icon = R.drawable.ic_figma_profile_business,
                isMock = true,
            ),
            ProfileField(
                title = "Руководитель",
                value = "agent",
                icon = R.drawable.ic_figma_profile_manager,
                isMock = true,
            ),
            ProfileField(
                title = "День рождения",
                value = "25.10.2000",
                icon = R.drawable.ic_figma_profile_birthday,
                isMock = true,
            ),
        )
    }
}

data class ProfileField(
    val title: String,
    val value: String,
    @param:DrawableRes val icon: Int,
    val isMock: Boolean = false,
)

internal fun resolveSharedChannels(
    userUuid: String,
    streams: List<Stream>,
    bindings: List<StreamBindingResponseData>?,
): List<Stream> {
    if (bindings == null) return emptyList()

    val streamUuids = bindings
        .asSequence()
        .filter { it.userUuid == userUuid }
        .map { it.streamUuid }
        .toSet()
    return if (streamUuids.isEmpty()) {
        streams.filterNot(Stream::isPrivate).take(5)
    } else {
        streams.filter { it.uuid in streamUuids }
    }
}
