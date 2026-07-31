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

    @Test
    fun `push sound is scoped by realm and ambiguous realms use default`() =
        runBlocking {
            val first = account("account-a", "https://one.example")
            val second = account("account-b", "https://two.example/")
            val preferences = mapOf(
                first.accountId to WorkspaceNotificationSound.GLASS,
                second.accountId to WorkspaceNotificationSound.PULSE,
            )
            val resolve: suspend (
                String,
                List<WorkspaceAccount>,
            ) -> WorkspaceNotificationSound = { realm, accounts ->
                resolveWorkspaceNotificationSoundForRealm(
                    realmUrl = realm,
                    accounts = { accounts },
                    preferencesForAccount = { owner ->
                        WorkspaceUiPreferences(
                            notificationSound = checkNotNull(preferences[owner]),
                        )
                    },
                )
            }

            assertEquals(
                WorkspaceNotificationSound.PULSE,
                resolve("https://TWO.example", listOf(first, second)),
            )
            assertEquals(
                WorkspaceNotificationSound.DEFAULT,
                resolve("https://missing.example", listOf(first, second)),
            )
            assertEquals(
                WorkspaceNotificationSound.DEFAULT,
                resolve(
                    "https://one.example",
                    listOf(
                        first,
                        first.copy(accountId = "account-a-2"),
                    ),
                ),
            )
        }

    private fun account(
        accountId: String,
        baseUrl: String,
    ) = WorkspaceAccount(
        accountId = accountId,
        baseUrl = baseUrl,
        projectId = "$accountId-project",
        projectName = accountId,
        userId = "$accountId-user",
        login = accountId,
    )
}
