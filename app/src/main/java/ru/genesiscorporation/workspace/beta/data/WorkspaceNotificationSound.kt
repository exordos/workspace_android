package ru.genesiscorporation.workspace.beta.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import androidx.annotation.RawRes
import kotlinx.coroutines.flow.first
import ru.genesiscorporation.workspace.beta.R
import ru.genesiscorporation.workspace.beta.data.navigation.canonicalWorkspaceBaseUrl
import ru.genesiscorporation.workspace.beta.data.navigation.canonicalWorkspaceRealmUrl

internal data class WorkspaceNotificationChannelSpec(
    val id: String,
    val name: String,
    @param:RawRes val soundResourceId: Int?,
)

internal fun workspaceNotificationChannelSpec(
    sound: WorkspaceNotificationSound,
): WorkspaceNotificationChannelSpec = when (sound) {
    WorkspaceNotificationSound.DEFAULT -> WorkspaceNotificationChannelSpec(
        id = "workspace_messages_default_v1",
        name = "Сообщения — обычный звук",
        soundResourceId = R.raw.notification_default,
    )

    WorkspaceNotificationSound.SUBTLE -> WorkspaceNotificationChannelSpec(
        id = "workspace_messages_subtle_v1",
        name = "Сообщения — мягкий звук",
        soundResourceId = R.raw.notification_subtle,
    )

    WorkspaceNotificationSound.DIGITAL -> WorkspaceNotificationChannelSpec(
        id = "workspace_messages_digital_v1",
        name = "Сообщения — цифровой звук",
        soundResourceId = R.raw.notification_digital,
    )

    WorkspaceNotificationSound.GLASS -> WorkspaceNotificationChannelSpec(
        id = "workspace_messages_glass_v1",
        name = "Сообщения — стеклянный звук",
        soundResourceId = R.raw.notification_glass,
    )

    WorkspaceNotificationSound.PULSE -> WorkspaceNotificationChannelSpec(
        id = "workspace_messages_pulse_v1",
        name = "Сообщения — импульс",
        soundResourceId = R.raw.notification_pulse,
    )

    WorkspaceNotificationSound.NONE -> WorkspaceNotificationChannelSpec(
        id = "workspace_messages_silent_v1",
        name = "Сообщения — без звука",
        soundResourceId = null,
    )
}

internal suspend fun resolveWorkspaceNotificationSound(
    activeAccountId: suspend () -> String?,
    preferencesForAccount: suspend (String) -> WorkspaceUiPreferences,
): WorkspaceNotificationSound {
    val accountId = activeAccountId() ?: return WorkspaceNotificationSound.DEFAULT
    return preferencesForAccount(accountId).notificationSound
}

internal suspend fun resolveWorkspaceNotificationSoundForRealm(
    realmUrl: String,
    accounts: suspend () -> List<WorkspaceAccount>,
    preferencesForAccount: suspend (String) -> WorkspaceUiPreferences,
): WorkspaceNotificationSound {
    val canonicalRealmUrl =
        canonicalWorkspaceRealmUrl(realmUrl)
            ?: return WorkspaceNotificationSound.DEFAULT
    val matchingAccount = accounts()
        .filter { account ->
            canonicalWorkspaceBaseUrl(account.baseUrl) == canonicalRealmUrl
        }
        .singleOrNull()
        ?: return WorkspaceNotificationSound.DEFAULT
    return preferencesForAccount(matchingAccount.accountId).notificationSound
}

suspend fun resolveActiveWorkspaceNotificationSound(
    context: Context,
): WorkspaceNotificationSound {
    val appContext = context.applicationContext
    val accountRepository = ApiKeyRepository(appContext)
    val preferencesRepository = WorkspaceUiPreferencesRepository(appContext)
    return resolveWorkspaceNotificationSound(
        activeAccountId = {
            accountRepository.activeAccountIdFlow.first()
        },
        preferencesForAccount = { accountId ->
            preferencesRepository.preferencesFlow(accountId).first()
        },
    )
}

suspend fun resolveWorkspaceNotificationSoundForRealm(
    context: Context,
    realmUrl: String,
): WorkspaceNotificationSound {
    val appContext = context.applicationContext
    val accountRepository = ApiKeyRepository(appContext)
    val preferencesRepository = WorkspaceUiPreferencesRepository(appContext)
    return resolveWorkspaceNotificationSoundForRealm(
        realmUrl = realmUrl,
        accounts = { accountRepository.accountsFlow.first() },
        preferencesForAccount = { accountId ->
            preferencesRepository.preferencesFlow(accountId).first()
        },
    )
}

object WorkspaceNotificationSoundController {
    private const val TAG = "WorkspaceNotification"

    private val notificationAudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun ensureChannel(
        context: Context,
        sound: WorkspaceNotificationSound,
    ): String {
        val spec = workspaceNotificationChannelSpec(sound)
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            spec.id,
            spec.name,
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Новые сообщения Workspace"
            lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            if (spec.soundResourceId == null) {
                setSound(null, null)
                enableVibration(false)
            } else {
                setSound(
                    rawResourceUri(context, spec.soundResourceId),
                    notificationAudioAttributes,
                )
            }
        }
        manager.createNotificationChannel(channel)
        return spec.id
    }

    fun preview(
        context: Context,
        sound: WorkspaceNotificationSound,
    ) {
        val resourceId = workspaceNotificationChannelSpec(sound).soundResourceId
            ?: return
        runCatching {
            MediaPlayer.create(
                context.applicationContext,
                resourceId,
                notificationAudioAttributes,
                0,
            )?.also { player ->
                player.setOnCompletionListener(MediaPlayer::release)
                player.setOnErrorListener { failedPlayer, _, _ ->
                    failedPlayer.release()
                    true
                }
                player.start()
            }
        }.onFailure { exception ->
            Log.w(TAG, "Could not preview the selected notification sound", exception)
        }
    }

    fun activate(
        context: Context,
        sound: WorkspaceNotificationSound,
    ): Boolean = runCatching {
        ensureChannel(context, sound)
        preview(context, sound)
        true
    }.onFailure { exception ->
        Log.w(TAG, "Could not activate the selected notification sound", exception)
    }.getOrDefault(false)

    private fun rawResourceUri(
        context: Context,
        @RawRes resourceId: Int,
    ): Uri = Uri.parse(
        "android.resource://${context.packageName}/raw/${
            context.resources.getResourceEntryName(resourceId)
        }",
    )
}
