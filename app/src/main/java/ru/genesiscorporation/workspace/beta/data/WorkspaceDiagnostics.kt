package ru.genesiscorporation.workspace.beta.data

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import java.time.Instant
import java.time.format.DateTimeFormatter
import ru.genesiscorporation.workspace.beta.BuildConfig

enum class WorkspaceNetworkStatus {
    ONLINE,
    LIMITED,
    OFFLINE,
    UNKNOWN,
}

data class WorkspaceDiagnosticsSnapshot(
    val generatedAt: Instant,
    val appVersion: String,
    val versionCode: Int,
    val buildType: String,
    val androidRelease: String,
    val androidApi: Int,
    val deviceManufacturer: String,
    val deviceModel: String,
    val networkStatus: WorkspaceNetworkStatus,
    val notificationPermissionGranted: Boolean,
    val notificationsEnabled: Boolean,
    val workspaceNotificationChannelCount: Int,
    val savedAccountCount: Int,
    val attachmentCacheBytes: Long,
    val themeMode: WorkspaceThemeMode,
    val chatListDensity: ChatListDensity,
    val notificationSound: WorkspaceNotificationSound,
    val prioritizePersonalUnread: Boolean,
    val prioritizeUnmutedUnreadChannels: Boolean,
)

fun collectWorkspaceDiagnostics(
    context: Context,
    savedAccountCount: Int,
    attachmentCacheBytes: Long,
    preferences: WorkspaceUiPreferences,
    generatedAt: Instant = Instant.now(),
): WorkspaceDiagnosticsSnapshot {
    val appContext = context.applicationContext
    val notificationManager =
        appContext.getSystemService(NotificationManager::class.java)
    return WorkspaceDiagnosticsSnapshot(
        generatedAt = generatedAt,
        appVersion = BuildConfig.VERSION_NAME,
        versionCode = BuildConfig.VERSION_CODE,
        buildType = BuildConfig.BUILD_TYPE,
        androidRelease = Build.VERSION.RELEASE,
        androidApi = Build.VERSION.SDK_INT,
        deviceManufacturer = Build.MANUFACTURER,
        deviceModel = Build.MODEL,
        networkStatus = resolveWorkspaceNetworkStatus(appContext),
        notificationPermissionGranted =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    appContext,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED,
        notificationsEnabled =
            NotificationManagerCompat.from(appContext).areNotificationsEnabled(),
        workspaceNotificationChannelCount = notificationManager
            .notificationChannels
            .count { channel ->
                channel.id.startsWith(WORKSPACE_MESSAGE_CHANNEL_PREFIX)
            },
        savedAccountCount = savedAccountCount.coerceAtLeast(0),
        attachmentCacheBytes = attachmentCacheBytes.coerceAtLeast(0L),
        themeMode = preferences.themeMode,
        chatListDensity = preferences.chatListDensity,
        notificationSound = preferences.notificationSound,
        prioritizePersonalUnread = preferences.prioritizePersonalUnread,
        prioritizeUnmutedUnreadChannels =
            preferences.prioritizeUnmutedUnreadChannels,
    )
}

fun renderWorkspaceDiagnostics(
    snapshot: WorkspaceDiagnosticsSnapshot,
): String = buildString {
    appendLine("CASSI Workspace diagnostics")
    appendLine(
        "generated_at=${
            DateTimeFormatter.ISO_INSTANT.format(snapshot.generatedAt)
        }",
    )
    appendLine("app_version=${snapshot.appVersion.asDiagnosticValue()}")
    appendLine("version_code=${snapshot.versionCode}")
    appendLine("build_type=${snapshot.buildType.asDiagnosticValue()}")
    appendLine(
        "android=${snapshot.androidRelease.asDiagnosticValue()} " +
            "(API ${snapshot.androidApi})",
    )
    appendLine(
        "device=${
            "${snapshot.deviceManufacturer} ${snapshot.deviceModel}"
                .asDiagnosticValue()
        }",
    )
    appendLine("network=${snapshot.networkStatus.name.lowercase()}")
    appendLine(
        "notification_permission=${
            snapshot.notificationPermissionGranted.asStatus()
        }",
    )
    appendLine(
        "notifications_enabled=${snapshot.notificationsEnabled.asStatus()}",
    )
    appendLine(
        "workspace_notification_channels=${
            snapshot.workspaceNotificationChannelCount
        }",
    )
    appendLine("saved_accounts=${snapshot.savedAccountCount}")
    appendLine("attachment_cache_bytes=${snapshot.attachmentCacheBytes}")
    appendLine("theme=${snapshot.themeMode.name.lowercase()}")
    appendLine("chat_density=${snapshot.chatListDensity.name.lowercase()}")
    appendLine(
        "notification_sound=${snapshot.notificationSound.name.lowercase()}",
    )
    appendLine(
        "prioritize_personal_unread=${
            snapshot.prioritizePersonalUnread.asStatus()
        }",
    )
    append(
        "prioritize_unmuted_unread_channels=${
            snapshot.prioritizeUnmutedUnreadChannels.asStatus()
        }",
    )
}

private fun resolveWorkspaceNetworkStatus(
    context: Context,
): WorkspaceNetworkStatus = runCatching {
    val manager = context.getSystemService(ConnectivityManager::class.java)
    val network = manager.activeNetwork ?: return@runCatching WorkspaceNetworkStatus.OFFLINE
    val capabilities = manager.getNetworkCapabilities(network)
        ?: return@runCatching WorkspaceNetworkStatus.UNKNOWN
    when {
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ->
            WorkspaceNetworkStatus.ONLINE
        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ->
            WorkspaceNetworkStatus.LIMITED
        else -> WorkspaceNetworkStatus.OFFLINE
    }
}.getOrDefault(WorkspaceNetworkStatus.UNKNOWN)

private fun Boolean.asStatus(): String = if (this) "yes" else "no"

private fun String.asDiagnosticValue(): String =
    replace(Regex("""[\p{Cc}\p{Cf}]+"""), " ")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(MAX_DIAGNOSTIC_VALUE_LENGTH)
        .ifBlank { "unknown" }

private const val WORKSPACE_MESSAGE_CHANNEL_PREFIX = "workspace_messages_"
private const val MAX_DIAGNOSTIC_VALUE_LENGTH = 96
