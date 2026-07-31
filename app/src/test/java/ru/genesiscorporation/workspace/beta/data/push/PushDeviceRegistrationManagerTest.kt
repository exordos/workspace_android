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

        assertTrue(manager.registerCurrentTokenWithRetry("account-a"))

        assertEquals("registration-account-a", remote.registrationUuid)
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

        manager.registerCurrentTokenWithRetry("account-a")
        assertTrue(manager.deleteRegistration("account-a"))

        assertEquals("registration-account-a", remote.deletedRegistrationUuid)
    }

    @Test
    fun deletionSuppressesTokenRefreshUntilTheNextAuthenticatedRegistration() = runBlocking {
        val remote = RecordingRemoteDataSource()
        val manager = PushDeviceRegistrationManager(
            tokenProvider = FixedTokenProvider("current-token"),
            identityProvider = FixedIdentityProvider(),
            remoteDataSource = remote,
        )

        assertTrue(manager.registerCurrentTokenWithRetry("account-a"))
        assertTrue(manager.deleteRegistration("account-a"))
        remote.request = null

        assertTrue(
            !manager.registerTokenWithRetry(
                "account-a",
                "late-refresh-token",
            ),
        )
        assertEquals(null, remote.request)

        assertTrue(manager.registerCurrentTokenWithRetry("account-a"))
        assertEquals("current-token", remote.request?.registrationToken)
    }

    @Test
    fun accountSwitchUsesIndependentRegistrationsAndFencesTheOldOwner() =
        runBlocking {
            val remote = RecordingRemoteDataSource()
            val manager = PushDeviceRegistrationManager(
                tokenProvider = FixedTokenProvider("fcm-token"),
                identityProvider = FixedIdentityProvider(),
                remoteDataSource = remote,
            )

            assertTrue(manager.registerCurrentTokenWithRetry("account-a"))
            assertTrue(manager.registerCurrentTokenWithRetry("account-b"))
            assertTrue(
                !manager.registerTokenWithRetry(
                    "account-a",
                    "stale-owner-token",
                ),
            )

            assertEquals(
                listOf(
                    "registration-account-a",
                    "registration-account-b",
                ),
                remote.registeredUuids,
            )
        }

    @Test
    fun ownerVerifierStopsRegistrationAfterCredentialSwitch() = runBlocking {
        var activeOwner = "account-a"
        val remote = RecordingRemoteDataSource()
        val manager = PushDeviceRegistrationManager(
            tokenProvider = FixedTokenProvider("fcm-token"),
            identityProvider = FixedIdentityProvider(),
            remoteDataSource = remote,
            isOwnerActive = { ownerKey -> ownerKey == activeOwner },
            retryDelaysMillis = longArrayOf(),
        )

        assertTrue(manager.registerCurrentTokenWithRetry("account-a"))
        activeOwner = "account-b"

        assertTrue(
            !manager.registerTokenWithRetry(
                "account-a",
                "stale-owner-token",
            ),
        )
        assertEquals(1, remote.registeredUuids.size)
    }

    @Test
    fun legacyInstallationRegistrationIsCleanedBeforeScopedRegistration() =
        runBlocking {
            val remote = RecordingRemoteDataSource()
            val manager = PushDeviceRegistrationManager(
                tokenProvider = FixedTokenProvider("fcm-token"),
                identityProvider =
                    FixedIdentityProvider(
                        legacyRegistrationUuid = "legacy-registration",
                    ),
                remoteDataSource = remote,
            )

            assertTrue(manager.registerCurrentTokenWithRetry("account-a"))

            assertEquals("legacy-registration", remote.deletedRegistrationUuid)
            assertEquals("registration-account-a", remote.registrationUuid)
        }

    @Test
    fun failedLogoutCleanupKeepsCurrentOwnerEligibleForTokenRotation() =
        runBlocking {
            val remote = RecordingRemoteDataSource()
            val manager = PushDeviceRegistrationManager(
                tokenProvider = FixedTokenProvider("fcm-token"),
                identityProvider = FixedIdentityProvider(),
                remoteDataSource = remote,
            )

            assertTrue(manager.registerCurrentTokenWithRetry("account-a"))
            remote.deleteSucceeded = false
            assertTrue(!manager.deleteRegistration("account-a"))
            remote.deleteSucceeded = true

            assertTrue(
                manager.registerTokenWithRetry(
                    "account-a",
                    "rotated-token",
                ),
            )
            assertEquals("rotated-token", remote.request?.registrationToken)
        }

    private class FixedTokenProvider(
        private val token: String,
    ) : PushRegistrationTokenProvider {
        override suspend fun currentToken() = token
    }

    private class FixedIdentityProvider(
        private val legacyRegistrationUuid: String? = null,
    ) : PushDeviceIdentityProvider {
        override suspend fun getOrCreateIdentity(
            ownerKey: String,
        ) = PushDeviceIdentity(
            registrationUuid = "registration-$ownerKey",
            keyUuid = "key-uuid",
            publicKey = "public-key",
        )

        override suspend fun getOrCreateRegistrationUuid(
            ownerKey: String,
        ) = "registration-$ownerKey"

        override suspend fun legacyRegistrationUuid() =
            legacyRegistrationUuid
    }

    private class RecordingRemoteDataSource : PushDeviceRemoteDataSource {
        var registrationUuid: String? = null
        var request: PushDeviceRequestData? = null
        var deletedRegistrationUuid: String? = null
        val registeredUuids = mutableListOf<String>()
        var deleteSucceeded = true

        override suspend fun register(
            registrationUuid: String,
            data: PushDeviceRequestData,
        ): Boolean {
            this.registrationUuid = registrationUuid
            registeredUuids += registrationUuid
            request = data
            return true
        }

        override suspend fun delete(registrationUuid: String): Boolean {
            deletedRegistrationUuid = registrationUuid
            return deleteSucceeded
        }
    }
}
