package ru.genesiscorporation.workspace.beta.data.push

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.PUSH_DEVICE_HPKE_ALGORITHM
import ru.genesiscorporation.workspace.beta.data.remote.dto.PUSH_DEVICE_HPKE_KIND
import ru.genesiscorporation.workspace.beta.data.remote.dto.PushDeviceRequestData

class PushDeviceRegistrationManagerTest {
    @Test
    fun registrationUsesStableIdentityAndExactWorkspaceContract() = runBlocking {
        val remote = RecordingRemoteDataSource()
        val manager = PushDeviceRegistrationManager(
            tokenProvider = FixedTokenProvider("fcm-token"),
            identityProvider = FixedIdentityProvider(),
            remoteDataSource = remote,
        )

        assertTrue(manager.registerCurrentTokenWithRetry())

        assertEquals("registration-uuid", remote.registrationUuid)
        val request = checkNotNull(remote.request)
        assertEquals("fcm", request.transport)
        assertEquals("android", request.platform)
        assertEquals("fcm-token", request.registrationToken)
        assertEquals(PUSH_DEVICE_HPKE_KIND, request.encryption.kind)
        assertEquals(PUSH_DEVICE_HPKE_ALGORITHM, request.encryption.algorithm)
        assertEquals("key-uuid", request.encryption.keyUuid)
        assertEquals("public-key", request.encryption.publicKey)
    }

    @Test
    fun deletionUsesTheSameInstallationRegistrationUuid() = runBlocking {
        val remote = RecordingRemoteDataSource()
        val manager = PushDeviceRegistrationManager(
            tokenProvider = FixedTokenProvider("unused"),
            identityProvider = FixedIdentityProvider(),
            remoteDataSource = remote,
        )

        assertTrue(manager.deleteRegistration())

        assertEquals("registration-uuid", remote.deletedRegistrationUuid)
    }

    @Test
    fun deletionSuppressesTokenRefreshUntilTheNextAuthenticatedRegistration() = runBlocking {
        val remote = RecordingRemoteDataSource()
        val manager = PushDeviceRegistrationManager(
            tokenProvider = FixedTokenProvider("current-token"),
            identityProvider = FixedIdentityProvider(),
            remoteDataSource = remote,
        )

        assertTrue(manager.registerCurrentTokenWithRetry())
        assertTrue(manager.deleteRegistration())
        remote.request = null

        assertTrue(!manager.registerTokenWithRetry("late-refresh-token"))
        assertEquals(null, remote.request)

        assertTrue(manager.registerCurrentTokenWithRetry())
        assertEquals("current-token", remote.request?.registrationToken)
    }

    private class FixedTokenProvider(
        private val token: String,
    ) : PushRegistrationTokenProvider {
        override suspend fun currentToken() = token
    }

    private class FixedIdentityProvider : PushDeviceIdentityProvider {
        override suspend fun getOrCreateIdentity() = PushDeviceIdentity(
            registrationUuid = "registration-uuid",
            keyUuid = "key-uuid",
            publicKey = "public-key",
        )

        override suspend fun getOrCreateRegistrationUuid() = "registration-uuid"
    }

    private class RecordingRemoteDataSource : PushDeviceRemoteDataSource {
        var registrationUuid: String? = null
        var request: PushDeviceRequestData? = null
        var deletedRegistrationUuid: String? = null

        override suspend fun register(
            registrationUuid: String,
            data: PushDeviceRequestData,
        ): Boolean {
            this.registrationUuid = registrationUuid
            request = data
            return true
        }

        override suspend fun delete(registrationUuid: String): Boolean {
            deletedRegistrationUuid = registrationUuid
            return true
        }
    }
}
