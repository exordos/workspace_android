package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class ApiKeyRepositoryInstrumentedTest {
    @Test
    fun switchesAccountsWithoutCrossWritingTokensOrCaches() = runBlocking {
        val targetContext = ApplicationProvider.getApplicationContext<Context>()
        val context = IsolatedAndroidTestContext(
            targetContext,
            "account-repository",
        )
        val credentials = InMemoryCredentialStore()
        val clearedAccountData = mutableListOf<String>()
        var failingCleanupOwner: String? = null
        val repository = ApiKeyRepository(
            context = context,
            credentialStore = credentials,
            clearAccountLocalData = { ownerKey ->
                check(ownerKey != failingCleanupOwner) {
                    "Injected local-cleanup failure"
                }
                clearedAccountData += ownerKey
            },
        )

        try {
            repository.addBaseUrl(SERVER)
            repository.saveSessionCredentials(
                projectId = PROJECT_A,
                projectName = "Project A",
                organizationName = "Example",
                userId = USER,
                login = "cassi",
                accessToken = "access-a",
                refreshToken = "refresh-a",
            )
            val accountA = repository.activeAccountFlow.first()
                ?: error("First account was not saved")

            repository.beginAddAccount()
            repository.addBaseUrl(SERVER)
            repository.saveSessionCredentials(
                projectId = PROJECT_B,
                projectName = "Project B",
                organizationName = "Example",
                userId = USER,
                login = "cassi",
                accessToken = "access-b",
                refreshToken = "refresh-b",
            )
            val accountB = repository.activeAccountFlow.first()
                ?: error("Second account was not saved")

            assertEquals(2, repository.accountsFlow.first().size)
            assertEquals("access-b", repository.activeCredentialSnapshot().accessToken)

            assertTrue(repository.activateAccount(accountA.accountId))
            val firstSnapshot = repository.activeCredentialSnapshot()
            assertEquals(PROJECT_A, firstSnapshot.projectId)
            assertEquals("access-a", firstSnapshot.accessToken)

            assertTrue(repository.activateAccount(accountB.accountId))
            assertFalse(
                repository.saveRefreshedTokensIfActive(
                    expectedOwnerKey = accountA.accountId,
                    accessToken = "stale-access-a",
                    refreshToken = "stale-refresh-a",
                ),
            )
            assertFalse(repository.removeActiveAccountIfOwner(accountA.accountId))
            assertEquals("access-b", repository.activeCredentialSnapshot().accessToken)

            val cacheA = accountAttachmentCacheDirectory(
                context.cacheDir,
                accountA.accountId,
            ).apply { mkdirs() }
            val cacheB = accountAttachmentCacheDirectory(
                context.cacheDir,
                accountB.accountId,
            ).apply { mkdirs() }
            File(cacheA, "a.txt").writeText("a")
            File(cacheB, "b.txt").writeText("b")

            failingCleanupOwner = accountB.accountId
            assertTrue(
                runCatching {
                    repository.removeActiveAccountIfOwner(accountB.accountId)
                }.isFailure,
            )
            assertEquals(accountB.accountId, repository.activeAccountIdFlow.first())
            assertEquals(
                "access-b",
                credentials.read(accountB.accountId, Credential.ACCESS_TOKEN),
            )
            failingCleanupOwner = null
            assertTrue(repository.removeActiveAccountIfOwner(accountB.accountId))
            assertEquals(listOf(accountB.accountId), clearedAccountData)
            assertEquals(accountA.accountId, repository.activeAccountIdFlow.first())
            assertTrue(File(cacheA, "a.txt").exists())
            assertFalse(cacheB.exists())
            assertNull(
                credentials.read(accountB.accountId, Credential.ACCESS_TOKEN),
            )
            assertEquals(
                "access-a",
                credentials.read(accountA.accountId, Credential.ACCESS_TOKEN),
            )
            repository.clearAll()
            assertEquals(
                listOf(accountB.accountId, accountA.accountId),
                clearedAccountData,
            )
        } finally {
            repository.clearAll()
            context.cleanUp()
        }
    }

    private class InMemoryCredentialStore : CredentialStore {
        private val values = mutableMapOf<Pair<String, Credential>, String>()

        override fun read(accountKey: String, credential: Credential): String? =
            values[accountKey to credential]

        override fun write(
            accountKey: String,
            credential: Credential,
            value: String,
        ) {
            values[accountKey to credential] = value
        }

        override fun remove(accountKey: String, credential: Credential) {
            values.remove(accountKey to credential)
        }

        override fun clear() {
            values.clear()
        }
    }

    private companion object {
        const val SERVER = "https://workspace.example.com"
        const val USER = "11111111-1111-4111-8111-111111111111"
        const val PROJECT_A = "22222222-2222-4222-8222-222222222222"
        const val PROJECT_B = "33333333-3333-4333-8333-333333333333"
    }
}
