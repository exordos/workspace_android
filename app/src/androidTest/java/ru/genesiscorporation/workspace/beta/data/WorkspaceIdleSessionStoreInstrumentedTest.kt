package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WorkspaceIdleSessionStoreInstrumentedTest {
    @Test
    fun checkpointsAreOwnerScopedAndIndependentlyCleared() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = WorkspaceIdleSessionStore(context)
        val suffix = UUID.randomUUID().toString()
        val ownerA = "cassi-idle-a-$suffix"
        val ownerB = "cassi-idle-b-$suffix"

        try {
            store.writeLastInteractionAtMillis(ownerA, 1_000L)
            store.writeLastInteractionAtMillis(ownerB, 2_000L)
            store.clear(ownerA)

            assertNull(store.readLastInteractionAtMillis(ownerA))
            assertEquals(2_000L, store.readLastInteractionAtMillis(ownerB))
        } finally {
            store.clear(ownerA)
            store.clear(ownerB)
        }
    }
}
