package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.workspaceUiPreferencesDataStore by preferencesDataStore(
    name = "workspace_ui_preferences",
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    },
)

enum class WorkspaceThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

enum class ChatListDensity {
    STANDARD,
    COMPACT,
}

enum class WorkspaceNotificationSound {
    DEFAULT,
    SUBTLE,
    DIGITAL,
    GLASS,
    PULSE,
    NONE,
}

data class WorkspaceUiPreferences(
    val themeMode: WorkspaceThemeMode = WorkspaceThemeMode.SYSTEM,
    val prioritizePersonalUnread: Boolean = false,
    val prioritizeUnmutedUnreadChannels: Boolean = true,
    val chatListDensity: ChatListDensity = ChatListDensity.STANDARD,
    val notificationSound: WorkspaceNotificationSound =
        WorkspaceNotificationSound.DEFAULT,
    val authIdleTimeout: WorkspaceAuthIdleTimeout =
        WorkspaceAuthIdleTimeout.THREE_DAYS,
)

class WorkspaceUiPreferencesRepository(
    context: Context,
) {
    private val dataStore = context.workspaceUiPreferencesDataStore

    fun preferencesFlow(ownerKey: String): Flow<WorkspaceUiPreferences> {
        val key = preferencesKey(ownerKey)
        return dataStore.data
            .catch { exception ->
                if (exception is IOException) {
                    emit(emptyPreferences())
                } else {
                    throw exception
                }
            }
            .map { preferences ->
                decodeWorkspaceUiPreferences(preferences[key])
            }
    }

    suspend fun update(
        ownerKey: String,
        transform: (WorkspaceUiPreferences) -> WorkspaceUiPreferences,
    ) {
        val key = preferencesKey(ownerKey)
        dataStore.edit { preferences ->
            val current = decodeWorkspaceUiPreferences(preferences[key])
            preferences[key] = encodeWorkspaceUiPreferences(transform(current))
        }
    }

    private fun preferencesKey(ownerKey: String): Preferences.Key<String> {
        require(ownerKey.isNotBlank()) {
            "Workspace UI preferences require a non-blank owner"
        }
        return stringPreferencesKey(
            "workspace_ui_preferences_v1_${workspaceStorageKey(ownerKey)}",
        )
    }
}

@Serializable
private data class PersistedWorkspaceUiPreferences(
    val themeMode: String? = null,
    val prioritizePersonalUnread: Boolean? = null,
    val prioritizeUnmutedUnreadChannels: Boolean? = null,
    val chatListDensity: String? = null,
    val notificationSound: String? = null,
    val authIdleTimeout: String? = null,
)

private val workspaceUiPreferencesJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

internal fun decodeWorkspaceUiPreferences(
    encoded: String?,
): WorkspaceUiPreferences {
    val persisted = encoded
        ?.let {
            runCatching {
                workspaceUiPreferencesJson.decodeFromString<PersistedWorkspaceUiPreferences>(it)
            }.getOrNull()
        }
        ?: return WorkspaceUiPreferences()
    return WorkspaceUiPreferences(
        themeMode = persisted.themeMode
            ?.let { value ->
                WorkspaceThemeMode.entries.firstOrNull { it.name == value }
            }
            ?: WorkspaceThemeMode.SYSTEM,
        prioritizePersonalUnread = persisted.prioritizePersonalUnread ?: false,
        prioritizeUnmutedUnreadChannels =
            persisted.prioritizeUnmutedUnreadChannels ?: true,
        chatListDensity = persisted.chatListDensity
            ?.let { value ->
                ChatListDensity.entries.firstOrNull { it.name == value }
            }
            ?: ChatListDensity.STANDARD,
        notificationSound = persisted.notificationSound
            ?.let { value ->
                WorkspaceNotificationSound.entries.firstOrNull { it.name == value }
            }
            ?: WorkspaceNotificationSound.DEFAULT,
        authIdleTimeout = persisted.authIdleTimeout
            ?.let { value ->
                WorkspaceAuthIdleTimeout.entries.firstOrNull { it.name == value }
            }
            ?: WorkspaceAuthIdleTimeout.THREE_DAYS,
    )
}

internal fun encodeWorkspaceUiPreferences(
    preferences: WorkspaceUiPreferences,
): String = workspaceUiPreferencesJson.encodeToString(
    PersistedWorkspaceUiPreferences(
        themeMode = preferences.themeMode.name,
        prioritizePersonalUnread = preferences.prioritizePersonalUnread,
        prioritizeUnmutedUnreadChannels =
            preferences.prioritizeUnmutedUnreadChannels,
        chatListDensity = preferences.chatListDensity.name,
        notificationSound = preferences.notificationSound.name,
        authIdleTimeout = preferences.authIdleTimeout.name,
    ),
)
