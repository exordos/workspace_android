package ru.genesiscorporation.workspace.beta.data

import java.time.Instant
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceDiagnosticsTest {
    @Test
    fun `report is deterministic useful and has no identity fields`() {
        val report = renderWorkspaceDiagnostics(
            WorkspaceDiagnosticsSnapshot(
                generatedAt = Instant.parse("2026-07-30T12:00:00Z"),
                appVersion = "1.2.3",
                versionCode = 42,
                buildType = "debug",
                androidRelease = "14",
                androidApi = 34,
                deviceManufacturer = "Example",
                deviceModel = "Phone",
                networkStatus = WorkspaceNetworkStatus.ONLINE,
                notificationPermissionGranted = true,
                notificationsEnabled = false,
                workspaceNotificationChannelCount = 6,
                savedAccountCount = 2,
                attachmentCacheBytes = 1_572_864,
                themeMode = WorkspaceThemeMode.DARK,
                chatListDensity = ChatListDensity.COMPACT,
                notificationSound = WorkspaceNotificationSound.GLASS,
                prioritizePersonalUnread = true,
                prioritizeUnmutedUnreadChannels = false,
            ),
        )

        listOf(
            "generated_at=2026-07-30T12:00:00Z",
            "app_version=1.2.3",
            "network=online",
            "notification_permission=yes",
            "notifications_enabled=no",
            "workspace_notification_channels=6",
            "saved_accounts=2",
            "attachment_cache_bytes=1572864",
            "notification_sound=glass",
        ).forEach { expected ->
            assertTrue(report.contains(expected))
        }
        listOf(
            "base_url",
            "server_url",
            "project_id",
            "user_id",
            "email",
            "access_token",
            "refresh_token",
            "message",
        ).forEach { forbidden ->
            assertFalse(report.contains(forbidden))
        }
    }

    @Test
    fun `environment labels cannot inject extra report lines or grow unbounded`() {
        val report = renderWorkspaceDiagnostics(
            WorkspaceDiagnosticsSnapshot(
                generatedAt = Instant.EPOCH,
                appVersion = "1.0\nforged=value",
                versionCode = 1,
                buildType = "debug\u0000build",
                androidRelease = "14\r\nextra=value",
                androidApi = 34,
                deviceManufacturer = "M".repeat(200),
                deviceModel = "\nModel",
                networkStatus = WorkspaceNetworkStatus.UNKNOWN,
                notificationPermissionGranted = false,
                notificationsEnabled = false,
                workspaceNotificationChannelCount = 0,
                savedAccountCount = 0,
                attachmentCacheBytes = 0,
                themeMode = WorkspaceThemeMode.SYSTEM,
                chatListDensity = ChatListDensity.STANDARD,
                notificationSound = WorkspaceNotificationSound.DEFAULT,
                prioritizePersonalUnread = false,
                prioritizeUnmutedUnreadChannels = false,
            ),
        )

        assertFalse(report.contains("\nforged="))
        assertFalse(report.contains("\nextra="))
        assertFalse(report.contains('\u0000'))
        assertTrue(
            report.lineSequence()
                .first { it.startsWith("device=") }
                .length <= "device=".length + 96,
        )
    }
}
