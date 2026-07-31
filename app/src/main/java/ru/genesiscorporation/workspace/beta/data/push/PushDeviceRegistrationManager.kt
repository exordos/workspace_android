package ru.genesiscorporation.workspace.beta.data.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import ru.genesiscorporation.workspace.beta.data.remote.ApiErrorKind
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeletePushDeviceRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.PUSH_DEVICE_HPKE_ALGORITHM
import ru.genesiscorporation.workspace.beta.data.remote.dto.PUSH_DEVICE_HPKE_KIND
import ru.genesiscorporation.workspace.beta.data.remote.dto.PushDeviceEncryptionData
import ru.genesiscorporation.workspace.beta.data.remote.dto.PushDeviceRequestData
import ru.genesiscorporation.workspace.beta.data.remote.dto.PutPushDeviceRequest
import java.util.concurrent.atomic.AtomicReference

interface PushRegistrationTokenProvider {
    suspend fun currentToken(): String
}

class FirebasePushRegistrationTokenProvider : PushRegistrationTokenProvider {
    override suspend fun currentToken(): String =
        FirebaseMessaging.getInstance().token.await()
}

interface PushDeviceRemoteDataSource {
    suspend fun register(
        registrationUuid: String,
        data: PushDeviceRequestData,
    ): Boolean

    suspend fun delete(registrationUuid: String): Boolean
}

class WorkspacePushDeviceRemoteDataSource(
    private val client: WorkspaceAPIClient,
) : PushDeviceRemoteDataSource {
    override suspend fun register(
        registrationUuid: String,
        data: PushDeviceRequestData,
    ): Boolean {
        val succeeded = client.performRequest(
            PutPushDeviceRequest(registrationUuid, data),
        ) is ApiResult.Success
        if (succeeded) Log.i(TAG, "Push device registration succeeded")
        return succeeded
    }

    override suspend fun delete(registrationUuid: String): Boolean {
        val succeeded = when (
            val result = client.performRequest(
                DeletePushDeviceRequest(registrationUuid),
            )
        ) {
            is ApiResult.Success -> true
            is ApiResult.Error -> result.error.kind == ApiErrorKind.NOT_FOUND
        }
        if (succeeded) Log.i(TAG, "Push device registration deleted")
        return succeeded
    }

    private companion object {
        private const val TAG = "PushDeviceRegistration"
    }
}

class PushDeviceRegistrationManager(
    private val tokenProvider: PushRegistrationTokenProvider,
    private val identityProvider: PushDeviceIdentityProvider,
    private val remoteDataSource: PushDeviceRemoteDataSource,
    private val isOwnerActive: suspend (String) -> Boolean = { true },
    private val retryDelaysMillis: LongArray = RETRY_DELAYS_MILLIS,
) {
    private val operationMutex = Mutex()
    private val activeOwner = AtomicReference<String?>(null)
    private val legacyCleanupAttemptedOwners = mutableSetOf<String>()

    suspend fun registerCurrentTokenWithRetry(ownerKey: String): Boolean {
        if (ownerKey.isBlank()) return false
        if (!isOwnerActive(ownerKey)) return false
        activeOwner.set(ownerKey)
        val token = try {
            tokenProvider.currentToken()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (exception: Exception) {
            Log.w(TAG, "Could not obtain the FCM registration token", exception)
            return false
        }
        return registerTokenWithRetry(ownerKey, token)
    }

    suspend fun registerTokenWithRetry(
        ownerKey: String,
        registrationToken: String,
    ): Boolean {
        if (!canRegister(ownerKey)) {
            return false
        }
        if (registrationToken.isBlank()) {
            Log.w(TAG, "Ignoring an empty FCM registration token")
            return false
        }

        retryDelaysMillis.forEachIndexed { attempt, retryDelay ->
            if (!canRegister(ownerKey)) {
                return false
            }
            if (registerToken(ownerKey, registrationToken)) return true
            if (!currentCoroutineContext().isActive) return false
            if (!canRegister(ownerKey)) {
                return false
            }
            Log.w(TAG, "Push device registration attempt ${attempt + 1} failed")
            delay(retryDelay)
        }
        return registerToken(ownerKey, registrationToken).also { registered ->
            if (!registered) Log.w(TAG, "Push device registration exhausted its retries")
        }
    }

    suspend fun deleteRegistration(ownerKey: String): Boolean {
        if (ownerKey.isBlank()) return false
        val disabledOwner = activeOwner.compareAndSet(ownerKey, null)
        return try {
            val registrationUuid =
                identityProvider.getOrCreateRegistrationUuid(ownerKey)
            val deleted = operationMutex.withLock {
                if (!isOwnerActive(ownerKey)) return@withLock false
                cleanupLegacyRegistration(ownerKey)
                remoteDataSource.delete(registrationUuid)
            }
            if (!deleted && disabledOwner && isOwnerActive(ownerKey)) {
                activeOwner.compareAndSet(null, ownerKey)
            }
            deleted
        } catch (cancellation: CancellationException) {
            if (disabledOwner) {
                activeOwner.compareAndSet(null, ownerKey)
            }
            throw cancellation
        } catch (exception: Exception) {
            if (
                disabledOwner &&
                runCatching { isOwnerActive(ownerKey) }.getOrDefault(false)
            ) {
                activeOwner.compareAndSet(null, ownerKey)
            }
            Log.w(TAG, "Could not delete the push device registration", exception)
            false
        }
    }

    fun deactivate() {
        activeOwner.set(null)
    }

    private suspend fun registerToken(
        ownerKey: String,
        registrationToken: String,
    ): Boolean = try {
        val identity = identityProvider.getOrCreateIdentity(ownerKey)
        operationMutex.withLock {
            if (!canRegister(ownerKey)) {
                false
            } else {
                cleanupLegacyRegistration(ownerKey)
                if (!canRegister(ownerKey)) {
                    false
                } else {
                    remoteDataSource.register(
                        registrationUuid = identity.registrationUuid,
                        data = PushDeviceRequestData(
                            transport = "fcm",
                            platform = "android",
                            registrationToken = registrationToken,
                            encryption = PushDeviceEncryptionData(
                                kind = PUSH_DEVICE_HPKE_KIND,
                                algorithm = PUSH_DEVICE_HPKE_ALGORITHM,
                                keyUuid = identity.keyUuid,
                                publicKey = identity.publicKey,
                            ),
                        ),
                    )
                }
            }
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (exception: Exception) {
        Log.w(TAG, "Could not prepare the push device registration", exception)
        false
    }

    private fun isRegistrationAllowed(ownerKey: String): Boolean =
        activeOwner.get() == ownerKey

    private suspend fun canRegister(ownerKey: String): Boolean {
        if (!isRegistrationAllowed(ownerKey)) return false
        if (isOwnerActive(ownerKey)) return true
        activeOwner.compareAndSet(ownerKey, null)
        return false
    }

    private suspend fun cleanupLegacyRegistration(ownerKey: String) {
        if (ownerKey in legacyCleanupAttemptedOwners) return
        val legacyRegistrationUuid =
            identityProvider.legacyRegistrationUuid() ?: run {
                legacyCleanupAttemptedOwners += ownerKey
                return
            }
        if (remoteDataSource.delete(legacyRegistrationUuid)) {
            legacyCleanupAttemptedOwners += ownerKey
        }
    }

    companion object {
        private const val TAG = "PushDeviceRegistration"
        private val RETRY_DELAYS_MILLIS = longArrayOf(1_000, 5_000, 30_000)
    }
}
