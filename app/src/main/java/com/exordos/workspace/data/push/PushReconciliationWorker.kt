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

package com.exordos.workspace.data.push

import android.content.Context
import android.net.ConnectivityManager
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import com.exordos.workspace.data.ServerRepository
import com.exordos.workspace.data.remote.ApiResult
import com.exordos.workspace.data.remote.WorkspaceAPIClient
import com.exordos.workspace.data.remote.dto.MessagesByIdsRequest
import com.exordos.workspace.data.remote.dto.OwnUserRequest
import java.util.concurrent.TimeUnit

/** Repair missed read/delete updates through the ordinary authenticated message API. */
class PushReconciliationWorker(
    context: Context,
    parameters: WorkerParameters,
    private val client: WorkspaceAPIClient,
    private val serverRepository: ServerRepository,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        workerMutex.withLock {
            val keys = PushKeyStore(applicationContext)
            val inbox = PushInbox(applicationContext)
            val notifications = WorkspacePushNotifications(applicationContext)
            // Local restoration must also run offline; Android may age out OS cards independently.
            notifications.restore()
            if (applicationContext.getSystemService(ConnectivityManager::class.java).activeNetwork == null) return@withLock Result.success()
            var retry = false
            try {
                for (server in serverRepository.serversFlow.first()) {
                    val registration = keys.get(server.id)?.takeIf { !it.disabled && it.projectUuid == server.projectUuid } ?: continue
                    if (server.needsToRelogin || serverRepository.tokensFor(server.id) == null) continue
                    if (inbox.pending(registration, 0).isEmpty()) continue
                    val own = client.performRequest(OwnUserRequest(), server.id, server)
                    if (own !is ApiResult.Success) { retry = true; continue }
                    if (serverRepository.getServer(server.id) != server || keys.get(server.id) != registration) continue
                    if (own.value.uuid != registration.userUuid) {
                        if (notifications.retireRegistration(registration)) PushRegistrationWorker.schedule(applicationContext)
                        continue
                    }
                    var after = 0L
                    while (serverRepository.getServer(server.id) == server && keys.get(server.id) == registration) {
                        val batch = inbox.pending(registration, after)
                        if (batch.isEmpty()) break
                        val response = client.performRequest(MessagesByIdsRequest(batch.map { it.route.messageUuid }), server.id, server)
                        if (response !is ApiResult.Success) { retry = true; break }
                        if (serverRepository.getServer(server.id) != server || keys.get(server.id) != registration) break
                        if (response.value.any { it.projectId != null && it.projectId != registration.projectUuid }) { retry = true; break }
                        // A successful filtered snapshot omits deleted or no-longer-accessible messages.
                        val readable = response.value.associateBy { it.uuid }
                        notifications.reconciled(registration, batch.map { it.route.messageUuid }.filter {
                            readable[it] == null || readable[it]?.read == true
                        })
                        after = batch.last().sequence
                    }
                }
                notifications.restore()
                if (retry) Result.retry() else Result.success()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { Result.retry() }
        }
    }
    companion object {
        private val workerMutex = Mutex()
        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("workspace-push-status", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<PushReconciliationWorker>().setConstraints(network).build())
        }
        fun installPeriodic(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("workspace-push-status-periodic", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PushReconciliationWorker>(15, TimeUnit.MINUTES).build())
        }
    }
}
