package ru.genesiscorporation.workspace.beta.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceNotificationSoundTest {
    @Test
    fun `every sound has a stable unique channel and only none is silent`() {
        val specs = WorkspaceNotificationSound.entries.map(
            ::workspaceNotificationChannelSpec,
        )

        assertEquals(specs.size, specs.map { it.id }.distinct().size)
        assertTrue(specs.all { it.id.startsWith("workspace_messages_") })
        assertNull(
            workspaceNotificationChannelSpec(WorkspaceNotificationSound.NONE)
                .soundResourceId,
        )
        WorkspaceNotificationSound.entries
            .filterNot { it == WorkspaceNotificationSound.NONE }
            .forEach { sound ->
                assertTrue(
                    workspaceNotificationChannelSpec(sound).soundResourceId != null,
                )
            }
    }

    @Test
    fun `resolver uses default without an account and exact owner otherwise`() =
        runBlocking {
            assertEquals(
                WorkspaceNotificationSound.DEFAULT,
                resolveWorkspaceNotificationSound(
                    activeAccountId = { null },
                    preferencesForAccount = {
                        error("Preferences must not be read without an active account")
                    },
                ),
            )

            var requestedOwner: String? = null
            assertEquals(
                WorkspaceNotificationSound.PULSE,
                resolveWorkspaceNotificationSound(
                    activeAccountId = { "account-b" },
                    preferencesForAccount = { owner ->
                        requestedOwner = owner
                        WorkspaceUiPreferences(
                            notificationSound = WorkspaceNotificationSound.PULSE,
                        )
                    },
                ),
            )
            assertEquals("account-b", requestedOwner)
        }
}
