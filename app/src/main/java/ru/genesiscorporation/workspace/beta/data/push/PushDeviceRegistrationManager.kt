package ru.genesiscorporation.workspace.beta.data.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.DeletePushDeviceRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.PUSH_DEVICE_HPKE_ALGORITHM
import ru.genesiscorporation.workspace.beta.data.remote.dto.PUSH_DEVICE_HPKE_KIND
import ru.genesiscorporation.workspace.beta.data.remote.dto.PushDeviceEncryptionData
import ru.genesiscorporation.workspace.beta.data.remote.dto.PushDeviceRequestData
import ru.genesiscorporation.workspace.beta.data.remote.dto.PutPushDeviceRequest
import java.util.concurrent.atomic.AtomicBoolean

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
        val succeeded = client.performRequest(
            DeletePushDeviceRequest(registrationUuid),
        ) is ApiResult.Success
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
) {
    private val operationMutex = Mutex()
    private val registrationAllowed = AtomicBoolean(false)

    suspend fun registerCurrentTokenWithRetry(): Boolean {
        registrationAllowed.set(true)
        return runCatching { tokenProvider.currentToken() }
            .onFailure { Log.w(TAG, "Could not obtain the FCM registration token", it) }
            .getOrNull()
            ?.let { registerTokenWithRetry(it) }
            ?: false
    }

    suspend fun registerTokenWithRetry(registrationToken: String): Boolean {
        if (!registrationAllowed.get()) return false
        if (registrationToken.isBlank()) {
            Log.w(TAG, "Ignoring an empty FCM registration token")
            return false
        }

        RETRY_DELAYS_MILLIS.forEachIndexed { attempt, retryDelay ->
            if (!registrationAllowed.get()) return false
            if (registerToken(registrationToken)) return true
            if (!currentCoroutineContext().isActive) return false
            Log.w(TAG, "Push device registration attempt ${attempt + 1} failed")
            delay(retryDelay)
        }
        return registerToken(registrationToken).also { registered ->
            if (!registered) Log.w(TAG, "Push device registration exhausted its retries")
        }
    }

    suspend fun deleteRegistration(): Boolean {
        registrationAllowed.set(false)
        return runCatching {
            val registrationUuid = identityProvider.getOrCreateRegistrationUuid()
            operationMutex.withLock {
                remoteDataSource.delete(registrationUuid)
            }
        }.onFailure {
            Log.w(TAG, "Could not delete the push device registration", it)
        }.getOrDefault(false)
    }

    private suspend fun registerToken(registrationToken: String): Boolean = runCatching {
        val identity = identityProvider.getOrCreateIdentity()
        operationMutex.withLock {
            if (!registrationAllowed.get()) {
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
    }.onFailure {
        Log.w(TAG, "Could not prepare the push device registration", it)
    }.getOrDefault(false)

    companion object {
        private const val TAG = "PushDeviceRegistration"
        private val RETRY_DELAYS_MILLIS = longArrayOf(1_000, 5_000, 30_000)
    }
}
