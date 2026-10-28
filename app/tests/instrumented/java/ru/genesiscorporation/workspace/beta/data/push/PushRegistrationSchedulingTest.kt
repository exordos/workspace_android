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

import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.genesiscorporation.workspace.beta.WorkspaceApplication
import java.util.concurrent.TimeUnit

/** A new login/token must not wait behind an older registration's pending retry. */
class PushRegistrationSchedulingTest {
    @Test fun freshRegistrationSupersedesAnOlderPendingRequest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = (context.applicationContext as WorkspaceApplication).serverRepository
        val previousCallback = repository.onPushRegistrationNeeded
        val work = WorkManager.getInstance(context)
        val name = "workspace-push-registration"
        repository.onPushRegistrationNeeded = null
        work.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS)
        try {
            // The same ENQUEUED dependency state as a previous request in retry backoff,
            // without performing a network request or waiting for the backoff clock.
            val pending = OneTimeWorkRequestBuilder<PushRegistrationWorker>()
                .setInitialDelay(30, TimeUnit.MINUTES).build()
            work.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, pending)
                .result.get(10, TimeUnit.SECONDS)
            assertEquals(WorkInfo.State.ENQUEUED,
                work.getWorkInfoById(pending.id).get(10, TimeUnit.SECONDS)?.state)

            PushRegistrationWorker.schedule(context)
            val requests = work.getWorkInfosForUniqueWork(name).get(10, TimeUnit.SECONDS)
            val fresh = requests.single { it.id != pending.id }
            assertNotEquals("The new login/token is blocked behind old pending work",
                WorkInfo.State.BLOCKED, fresh.state)
            val previous = work.getWorkInfoById(pending.id).get(10, TimeUnit.SECONDS)
            assertTrue("Older pending work must be cancelled or removed",
                previous == null || previous.state == WorkInfo.State.CANCELLED)
        } finally {
            work.cancelUniqueWork(name).result.get(10, TimeUnit.SECONDS)
            repository.onPushRegistrationNeeded = previousCallback
            previousCallback?.invoke()
        }
    }
}
