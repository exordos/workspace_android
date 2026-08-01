package ru.genesiscorporation.workspace.beta.modules.profile

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.ChatListDensity
import ru.genesiscorporation.workspace.beta.data.WorkspaceNotificationSound
import ru.genesiscorporation.workspace.beta.data.WorkspaceAuthIdleTimeout
import ru.genesiscorporation.workspace.beta.data.WorkspaceThemeMode
import ru.genesiscorporation.workspace.beta.data.WorkspaceUiPreferences
import ru.genesiscorporation.workspace.beta.data.remote.ApiError
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.OwnUserRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ResetOwnAvatarRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UpdateOwnPresenceRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.data.push.PushDeviceRegistrationManager
import java.util.UUID

class ProfileViewModel(
    val userViewModel: UserViewModel,
    private val client: WorkspaceAPIClient,
    private val pushDeviceRegistrationManager: PushDeviceRegistrationManager,
    private val activateNotificationSound: (WorkspaceNotificationSound) -> Boolean = {
        true
    },
    private val readAttachmentCacheSizeBytes: (String) -> Long = { 0L },
    private val deleteAttachmentCache: (String) -> Boolean = { true },
): ViewModel() {
    val accounts = userViewModel.accounts
    val activeAccountId = userViewModel.activeAccountId
    val activeAccount = userViewModel.activeAccount
    val userData = userViewModel.userDataFlow
    val uiPreferences = userViewModel.uiPreferences

    private val _operationInProgress = MutableStateFlow(false)
    val operationInProgress: StateFlow<Boolean> = _operationInProgress
    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError
    private val _settingsSaving = MutableStateFlow(false)
    val settingsSaving: StateFlow<Boolean> = _settingsSaving
    private val _attachmentCacheSizeBytes = MutableStateFlow(0L)
    val attachmentCacheSizeBytes: StateFlow<Long> = _attachmentCacheSizeBytes
    private val _cacheClearing = MutableStateFlow(false)
    val cacheClearing: StateFlow<Boolean> = _cacheClearing
    private val _profileRefreshing = MutableStateFlow(false)
    val profileRefreshing: StateFlow<Boolean> = _profileRefreshing
    private val _profileMutationInProgress = MutableStateFlow(false)
    val profileMutationInProgress: StateFlow<Boolean> = _profileMutationInProgress
    private val _profileMutationSucceeded = MutableStateFlow<Boolean?>(null)
    val profileMutationSucceeded: StateFlow<Boolean?> = _profileMutationSucceeded
    private var cacheSizeRequestId = 0L
    private var profileRequestId = 0L

    init {
        viewModelScope.launch {
            activeAccountId.collectLatest { ownerKey ->
                _actionError.value = null
                _profileMutationSucceeded.value = null
                if (ownerKey == null) {
                    cacheSizeRequestId += 1L
                    profileRequestId += 1L
                    _attachmentCacheSizeBytes.value = 0L
                    _profileRefreshing.value = false
                } else {
                    try {
                        loadAttachmentCacheSize(ownerKey)
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (exception: Exception) {
                        if (activeAccountId.value == ownerKey) {
                            _attachmentCacheSizeBytes.value = 0L
                        }
                    }
                    refreshOwnProfile(ownerKey, reportFailure = false)
                }
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun refreshAttachmentCacheSize() {
        val ownerKey = activeAccountId.value ?: return
        viewModelScope.launch {
            loadAttachmentCacheSize(ownerKey)
        }
    }

    fun refreshProfile() {
        if (_profileRefreshing.value || _profileMutationInProgress.value) return
        val ownerKey = activeAccountId.value ?: return
        _actionError.value = null
        viewModelScope.launch {
            refreshOwnProfile(ownerKey, reportFailure = true)
        }
    }

    fun updateStatus(
        text: String,
        away: Boolean,
        clear: Boolean = false,
    ): Boolean {
        if (_profileMutationInProgress.value) return false
        val ownerKey = activeAccountId.value ?: run {
            _actionError.value = "Не удалось определить текущий аккаунт"
            return false
        }
        val userUuid = activeAccount.value?.userId ?: run {
            _actionError.value = "Не удалось определить пользователя"
            return false
        }
        val normalizedText = text.trim()
        if (!clear && normalizedText.length > MAX_STATUS_TEXT_LENGTH) {
            _actionError.value =
                "Статус должен быть не длиннее $MAX_STATUS_TEXT_LENGTH символов"
            return false
        }
        return runProfileMutation(
            ownerKey = ownerKey,
            expectedUserUuid = userUuid,
            errorPrefix = "Не удалось обновить статус",
        ) {
            client.performRequest(
                UpdateOwnPresenceRequest(
                    userUuid = userUuid,
                    status = if (!clear && away) "idle" else "active",
                    emoji = if (clear) {
                        null
                    } else {
                        userData.value?.statusEmoji
                    },
                    text = normalizedText.takeIf {
                        !clear && it.isNotEmpty()
                    },
                ),
            )
        }
    }

    fun uploadAvatar(
        context: Context,
        uri: Uri,
    ): Boolean {
        if (_profileMutationInProgress.value) return false
        val ownerKey = activeAccountId.value ?: run {
            _actionError.value = "Не удалось определить текущий аккаунт"
            return false
        }
        val userUuid = activeAccount.value?.userId ?: run {
            _actionError.value = "Не удалось определить пользователя"
            return false
        }
        return runProfileMutation(
            ownerKey = ownerKey,
            expectedUserUuid = userUuid,
            errorPrefix = "Не удалось обновить фото профиля",
        ) {
            client.uploadOwnAvatar(
                context = context.applicationContext,
                uri = uri,
                userUuid = userUuid,
            )
        }
    }

    fun removeAvatar(): Boolean {
        if (_profileMutationInProgress.value) return false
        val ownerKey = activeAccountId.value ?: run {
            _actionError.value = "Не удалось определить текущий аккаунт"
            return false
        }
        val userUuid = activeAccount.value?.userId ?: run {
            _actionError.value = "Не удалось определить пользователя"
            return false
        }
        return runProfileMutation(
            ownerKey = ownerKey,
            expectedUserUuid = userUuid,
            errorPrefix = "Не удалось удалить фото профиля",
        ) {
            client.performRequest(ResetOwnAvatarRequest(userUuid))
        }
    }

    fun addAccount() {
        runAccountOperation {
            userViewModel.beginAddAccountAndWait()
        }
    }

    fun switchAccount(accountId: String) {
        if (accountId == activeAccountId.value) return
        runAccountOperation {
            if (!userViewModel.switchAccountAndWait(accountId)) {
                _actionError.value = "Сохранённый аккаунт больше недоступен"
            }
        }
    }

    fun logout() {
        val ownerKey = activeAccountId.value ?: run {
            _actionError.value = "Не удалось определить текущий аккаунт"
            return
        }
        runAccountOperation {
            if (!pushDeviceRegistrationManager.deleteRegistration(ownerKey)) {
                if (activeAccountId.value == ownerKey) {
                    _actionError.value =
                        "Не удалось отключить уведомления. Проверьте сеть и повторите выход."
                }
                return@runAccountOperation
            }
            userViewModel.removeActiveAccountIfOwnerAndWait(ownerKey)
        }
    }

    fun setThemeMode(mode: WorkspaceThemeMode) {
        updatePreference(
            transform = { current ->
                current.copy(themeMode = mode)
            },
        )
    }

    fun setPrioritizePersonalUnread(enabled: Boolean) {
        updatePreference(
            transform = { current ->
                current.copy(prioritizePersonalUnread = enabled)
            },
        )
    }

    fun setPrioritizeUnmutedUnreadChannels(enabled: Boolean) {
        updatePreference(
            transform = { current ->
                current.copy(prioritizeUnmutedUnreadChannels = enabled)
            },
        )
    }

    fun setChatListDensity(density: ChatListDensity) {
        updatePreference(
            transform = { current ->
                current.copy(chatListDensity = density)
            },
        )
    }

    fun setNotificationSound(sound: WorkspaceNotificationSound) {
        updatePreference(
            transform = { current ->
                current.copy(notificationSound = sound)
            },
            onSaved = {
                if (!activateNotificationSound(sound)) {
                    _actionError.value =
                        "Настройка сохранена, но Android не создал канал уведомлений"
                }
            },
        )
    }

    fun setAuthIdleTimeout(timeout: WorkspaceAuthIdleTimeout) {
        updatePreference(
            transform = { current ->
                current.copy(authIdleTimeout = timeout)
            },
        )
    }

    fun clearCachedAttachments() {
        if (_cacheClearing.value) return
        val ownerKey = activeAccountId.value
        if (ownerKey == null) {
            _actionError.value = "Не удалось определить текущий аккаунт"
            return
        }
        _cacheClearing.value = true
        viewModelScope.launch {
            _actionError.value = null
            try {
                val cleared = withContext(Dispatchers.IO) {
                    deleteAttachmentCache(ownerKey)
                }
                if (!cleared && activeAccountId.value == ownerKey) {
                    _actionError.value = "Не удалось очистить кэш вложений"
                } else if (activeAccountId.value == ownerKey) {
                    loadAttachmentCacheSize(ownerKey)
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                if (activeAccountId.value == ownerKey) {
                    _actionError.value = "Не удалось очистить кэш вложений"
                }
            } finally {
                _cacheClearing.value = false
            }
        }
    }

    private suspend fun loadAttachmentCacheSize(ownerKey: String) {
        cacheSizeRequestId += 1L
        val requestId = cacheSizeRequestId
        val sizeBytes = withContext(Dispatchers.IO) {
            readAttachmentCacheSizeBytes(ownerKey)
        }
        if (
            requestId == cacheSizeRequestId &&
            activeAccountId.value == ownerKey
        ) {
            _attachmentCacheSizeBytes.value = sizeBytes
        }
    }

    private suspend fun refreshOwnProfile(
        ownerKey: String,
        reportFailure: Boolean,
    ) {
        if (_profileMutationInProgress.value) return
        val account = activeAccount.value
        if (account?.accountId != ownerKey) return
        val expectedUserUuid = account.userId
        profileRequestId += 1L
        val requestId = profileRequestId
        _profileRefreshing.value = true
        try {
            when (val result = client.performRequest(OwnUserRequest())) {
                is ApiResult.Success -> {
                    if (
                        requestId == profileRequestId &&
                        activeAccountId.value == ownerKey
                    ) {
                        if (
                            profileBelongsToUser(
                                profileUuid = result.value.uuid,
                                expectedUserUuid = expectedUserUuid,
                            )
                        ) {
                            applyProfileSnapshot(ownerKey, result.value)
                        } else if (reportFailure || userData.value == null) {
                            _actionError.value =
                                "Не удалось обновить профиль: " +
                                    "сервер вернул некорректные данные"
                        }
                    }
                }
                is ApiResult.Error -> {
                    if (
                        requestId == profileRequestId &&
                        activeAccountId.value == ownerKey &&
                        (reportFailure || userData.value == null)
                    ) {
                        _actionError.value = profileErrorMessage(
                            "Не удалось обновить профиль",
                            result.error,
                        )
                    }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            if (
                requestId == profileRequestId &&
                activeAccountId.value == ownerKey &&
                (reportFailure || userData.value == null)
            ) {
                _actionError.value = "Не удалось обновить профиль"
            }
        } finally {
            if (requestId == profileRequestId) {
                _profileRefreshing.value = false
            }
        }
    }

    private fun runProfileMutation(
        ownerKey: String,
        expectedUserUuid: String,
        errorPrefix: String,
        request: suspend () -> ApiResult<UserResponseData, ApiError>,
    ): Boolean {
        if (_profileMutationInProgress.value) return false
        _profileMutationInProgress.value = true
        _profileMutationSucceeded.value = null
        _profileRefreshing.value = false
        _actionError.value = null
        profileRequestId += 1L
        val requestId = profileRequestId
        viewModelScope.launch {
            try {
                when (val result = request()) {
                    is ApiResult.Success -> {
                        if (
                            requestId == profileRequestId &&
                            activeAccountId.value == ownerKey
                        ) {
                            if (
                                profileBelongsToUser(
                                    profileUuid = result.value.uuid,
                                    expectedUserUuid = expectedUserUuid,
                                )
                            ) {
                                applyProfileSnapshot(ownerKey, result.value)
                                _profileMutationSucceeded.value = true
                            } else {
                                _actionError.value =
                                    "$errorPrefix: сервер вернул " +
                                        "некорректные данные"
                                _profileMutationSucceeded.value = false
                            }
                        }
                    }
                    is ApiResult.Error -> {
                        if (
                            requestId == profileRequestId &&
                            activeAccountId.value == ownerKey
                        ) {
                            _actionError.value = profileErrorMessage(
                                errorPrefix,
                                result.error,
                            )
                            _profileMutationSucceeded.value = false
                        }
                    }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                if (
                    requestId == profileRequestId &&
                    activeAccountId.value == ownerKey
                ) {
                    _actionError.value = errorPrefix
                    _profileMutationSucceeded.value = false
                }
            } finally {
                _profileMutationInProgress.value = false
                val currentOwnerKey = activeAccountId.value
                if (
                    currentOwnerKey != null &&
                    currentOwnerKey != ownerKey
                ) {
                    refreshOwnProfile(
                        ownerKey = currentOwnerKey,
                        reportFailure = false,
                    )
                }
            }
        }
        return true
    }

    private suspend fun applyProfileSnapshot(
        ownerKey: String,
        profile: UserResponseData,
    ) {
        if (activeAccountId.value != ownerKey) return
        userViewModel.userData = profile
        try {
            userViewModel.repo.saveCurrentAccountProfile(
                userId = profile.uuid,
                displayName = profile.displayableName(),
                email = profile.email,
                avatarUrn = profile.avatar,
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            if (activeAccountId.value == ownerKey) {
                _actionError.value =
                    "Профиль обновлён, но не удалось сохранить локальную копию"
            }
        }
    }

    private fun updatePreference(
        transform: (WorkspaceUiPreferences) -> WorkspaceUiPreferences,
        onSaved: () -> Unit = {},
    ) {
        if (_settingsSaving.value) return
        _settingsSaving.value = true
        viewModelScope.launch {
            _actionError.value = null
            try {
                if (!userViewModel.updateUiPreferences(transform)) {
                    _actionError.value = "Не удалось определить текущий аккаунт"
                } else {
                    onSaved()
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                _actionError.value = "Не удалось сохранить настройки"
            } finally {
                _settingsSaving.value = false
            }
        }
    }

    private fun runAccountOperation(block: suspend () -> Unit) {
        if (_operationInProgress.value) return
        _operationInProgress.value = true
        viewModelScope.launch {
            _actionError.value = null
            try {
                block()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (exception: Exception) {
                _actionError.value = "Не удалось изменить аккаунт"
            } finally {
                _operationInProgress.value = false
            }
        }
    }

    private companion object {
        const val MAX_STATUS_TEXT_LENGTH = 256
    }
}

internal fun profileErrorMessage(
    prefix: String,
    error: ApiError,
): String = when (error.kind) {
    ApiErrorKind.VALIDATION ->
        when (error.code) {
            "AVATAR_TOO_LARGE" -> "Фото профиля должно быть не больше 25 МБ"
            "AVATAR_EMPTY", "AVATAR_INVALID", "AVATAR_TYPE_MISMATCH" ->
                "Выберите изображение PNG, JPEG, GIF или WebP"
            "FILE_PERMISSION_DENIED", "FILE_READ_FAILED" ->
                "Выбранное изображение больше недоступно"
            else -> "$prefix: проверьте выбранные данные"
        }
    ApiErrorKind.FORBIDDEN -> "$prefix: действие запрещено"
    ApiErrorKind.RATE_LIMITED -> "$prefix: слишком много запросов, попробуйте позже"
    ApiErrorKind.TIMEOUT -> "$prefix: сервер не ответил вовремя"
    ApiErrorKind.NETWORK -> "$prefix: нет подключения к сети"
    ApiErrorKind.SERVER -> "$prefix: сервис временно недоступен"
    ApiErrorKind.CONFLICT -> "$prefix: аккаунт изменился, повторите действие"
    else -> prefix
}

internal fun profileBelongsToUser(
    profileUuid: String,
    expectedUserUuid: String,
): Boolean {
    val profileId = runCatching { UUID.fromString(profileUuid) }.getOrNull()
        ?: return false
    val expectedId = runCatching {
        UUID.fromString(expectedUserUuid)
    }.getOrNull() ?: return false
    return profileId == expectedId
}
