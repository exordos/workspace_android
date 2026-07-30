package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceUiPreferencesRepositoryInstrumentedTest {
    @Test
    fun preferencesPersistPerAccountAndConcurrentEditsDoNotClobber() = runBlocking {
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val context = IsolatedAndroidTestContext(
            targetContext,
            "ui-preferences",
        )
        val repository = WorkspaceUiPreferencesRepository(context)

        try {
            assertEquals(
                WorkspaceUiPreferences(),
                repository.preferencesFlow(OWNER_A).first(),
            )

            coroutineScope {
                launch {
                    repository.update(OWNER_A) {
                        it.copy(themeMode = WorkspaceThemeMode.DARK)
                    }
                }
                launch {
                    repository.update(OWNER_A) {
                        it.copy(
                            prioritizePersonalUnread = true,
                            chatListDensity = ChatListDensity.COMPACT,
                            notificationSound = WorkspaceNotificationSound.DIGITAL,
                        )
                    }
                }
            }

            val accountA = repository.preferencesFlow(OWNER_A).first()
            assertEquals(WorkspaceThemeMode.DARK, accountA.themeMode)
            assertTrue(accountA.prioritizePersonalUnread)
            assertEquals(ChatListDensity.COMPACT, accountA.chatListDensity)
            assertEquals(
                WorkspaceNotificationSound.DIGITAL,
                accountA.notificationSound,
            )

            val accountB = repository.preferencesFlow(OWNER_B).first()
            assertEquals(WorkspaceUiPreferences(), accountB)
            assertFalse(accountB.prioritizePersonalUnread)

            WorkspaceUiPreferencesRepository(context).update(OWNER_B) {
                it.copy(prioritizeUnmutedUnreadChannels = true)
            }
            assertTrue(
                repository
                    .preferencesFlow(OWNER_B)
                    .first()
                    .prioritizeUnmutedUnreadChannels,
            )
            assertEquals(accountA, repository.preferencesFlow(OWNER_A).first())
        } finally {
            context.cleanUp()
        }
    }

    private companion object {
        const val OWNER_A = "account-a"
        const val OWNER_B = "account-b"
    }
}
