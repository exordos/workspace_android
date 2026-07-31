package ru.genesiscorporation.workspace.beta.data.push

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.SessionCookieStore
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.ApiKeyRepository
import ru.genesiscorporation.workspace.beta.data.TinkConversationStateStore
import ru.genesiscorporation.workspace.beta.data.WorkspaceUiPreferencesRepository
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PushDeviceRemoteInstrumentedTest {
    @Test
    fun registerAndDeleteAgainstAuthenticatedWorkspace() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        assumeTrue(
            "Run with pushRemoteE2e=true on an explicitly authorized device",
            InstrumentationRegistry.getArguments().getString("pushRemoteE2e") == "true",
        )

        val context = instrumentation.targetContext
        val repository = ApiKeyRepository(context)
        assumeTrue(repository.baseUrlFlow.first() != null)
        assumeTrue(repository.accessTokenFlow.first() != null)

        val userViewModel = UserViewModel(
            repository,
            TinkConversationStateStore(context),
            WorkspaceUiPreferencesRepository(context),
        )
        val httpClient = HttpClient {
            install(ContentNegotiation) {
                json()
            }
        }
        val apiClient = WorkspaceAPIClient(
            httpClient,
            userViewModel,
            SessionCookieStore(),
        )
        val manager = PushDeviceRegistrationManager(
            tokenProvider = object : PushRegistrationTokenProvider {
                override suspend fun currentToken() =
                    "android-instrumented-${UUID.randomUUID()}"
            },
            identityProvider = TinkPushDeviceIdentityStore(
                context,
                "_remote_instrumented_test",
            ),
            remoteDataSource = WorkspacePushDeviceRemoteDataSource(apiClient),
            isOwnerActive = repository::isActiveCredentialOwner,
        )
        val ownerKey = checkNotNull(repository.activeCredentialSnapshot().ownerKey)

        try {
            assertTrue(manager.registerCurrentTokenWithRetry(ownerKey))
        } finally {
            assertTrue(manager.deleteRegistration(ownerKey))
            assertTrue(manager.deleteRegistration(ownerKey))
            httpClient.close()
        }
    }
}
