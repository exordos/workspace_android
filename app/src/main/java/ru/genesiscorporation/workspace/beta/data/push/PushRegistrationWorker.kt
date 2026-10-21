/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.genesiscorporation.workspace.beta.data.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.genesiscorporation.workspace.beta.data.ServerRepository
import ru.genesiscorporation.workspace.beta.data.remote.ApiResult
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.OwnUserRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.PushDeviceData
import ru.genesiscorporation.workspace.beta.data.remote.dto.PushDeviceEncryption
import ru.genesiscorporation.workspace.beta.data.remote.dto.PutPushDeviceRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.RemovePushDeviceRequest
import ru.genesiscorporation.workspace.beta.data.remote.dto.ServerSettingsRequest
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Reconciles the latest token after login, refresh, process death or a network outage. */
class PushRegistrationWorker(
    context: Context,
    parameters: WorkerParameters,
    private val client: WorkspaceAPIClient,
    private val serverRepository: ServerRepository,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val token = Tasks.await(FirebaseMessaging.getInstance().token, 30, TimeUnit.SECONDS)
            var retry = false
            for (server in serverRepository.serversFlow.first()) {
                if (server.needsToRelogin || server.projectUuid.isNullOrBlank() || serverRepository.tokensFor(server.id) == null) continue
                val completed = withTimeoutOrNull(15_000) {
                    serverRepository.pushRegistrationMutex.withLock {
                        if (serverRepository.getServer(server.id) != server) return@withLock
                        val keys = PushKeyStore(applicationContext)
                        if (keys.get(server.id)?.disabled == true) return@withLock
                        val settings = client.performRequest(ServerSettingsRequest(server.baseUrl), server.id, server)
                        if (settings !is ApiResult.Success) { retry = true; return@withLock }
                        val organization = settings.value.organizationUuid ?: return@withLock // older server
                        UUID.fromString(organization)
                        if (!settings.value.pushNotificationsEnabled) return@withLock
                        val own = client.performRequest(OwnUserRequest(), server.id, server)
                        if (own !is ApiResult.Success) { retry = true; return@withLock }
                        if (serverRepository.getServer(server.id) != server) return@withLock
                        keys.get(server.id)?.takeIf { it.userUuid != own.value.uuid || it.projectUuid != server.projectUuid || it.organizationUuid != organization }?.let {
                            // Same-account key changes can still retire the old UUID with the current session.
                            // Owner/project replacements are retired before their credentials change.
                            if (it.userUuid == own.value.uuid && it.projectUuid == server.projectUuid) {
                                client.performRequest(RemovePushDeviceRequest(it.deviceUuid), server.id, server)
                            }
                            WorkspacePushNotifications(applicationContext).cancelServer(server.id)
                        }
                        val registration = keys.getOrCreate(server.id, organization, requireNotNull(server.projectUuid), own.value.uuid)
                        val response = client.performRequest(PutPushDeviceRequest(registration.deviceUuid,
                            PushDeviceData(registrationToken = token, encryption = PushDeviceEncryption(
                                keyUuid = registration.keyUuid, publicKey = registration.publicKey))), server.id, server)
                        if (response !is ApiResult.Success) retry = true
                        else if (response.value.uuid != registration.deviceUuid || response.value.userUuid != registration.userUuid ||
                            response.value.projectUuid != registration.projectUuid) {
                            client.performRequest(RemovePushDeviceRequest(registration.deviceUuid), server.id, server)
                            keys.remove(server.id)
                            if (serverRepository.getServer(server.id) == server) serverRepository.setNeedsToRelogin(true, server.id)
                        }
                    }
                    true
                }
                if (completed != true) retry = true
            }
            if (retry) Result.retry() else Result.success()
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { Result.retry() }
    }

    companion object {
        fun schedule(context: Context) {
            // Reconcile the latest state now, including when older work is in retry backoff.
            WorkManager.getInstance(context).enqueueUniqueWork("workspace-push-registration", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<PushRegistrationWorker>().setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED).build()).build())
        }
        suspend fun unregister(
            context: Context,
            serverId: String,
            client: WorkspaceAPIClient,
            serverRepository: ServerRepository,
        ) {
            val keys = PushKeyStore(context)
            val registration = keys.get(serverId) ?: return
            // Disable receipt first. Offline logout still immediately stops local display.
            keys.save(registration.copy(disabled = true))
            WorkspacePushNotifications(context).cancelServer(serverId)
            val server = serverRepository.getServer(serverId) ?: return
            // Allow a cold HTTPS connection the same bounded budget as registration.
            withTimeoutOrNull(15_000) {
                client.performRequest(RemovePushDeviceRequest(registration.deviceUuid), serverId, server)
            }
        }
    }
}
