package ru.genesiscorporation.workspace.beta.data

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class RealtimeCursorStoreTest {
    @Test
    fun `in-memory cursor state is account scoped and clear is selective`() =
        runBlocking {
            val store = InMemoryRealtimeCursorStore()
            val first = PersistedRealtimeCursor(10, "generation-a")
            val second = PersistedRealtimeCursor(20, "generation-b")

            store.write(ACCOUNT_A, first)
            store.write(ACCOUNT_B, second)

            assertEquals(first, store.read(ACCOUNT_A))
            assertEquals(second, store.read(ACCOUNT_B))

            store.clearAccount(ACCOUNT_A)

            assertNull(store.read(ACCOUNT_A))
            assertEquals(second, store.read(ACCOUNT_B))
        }

    @Test
    fun `persisted cursor rejects malformed server state`() {
        assertThrows(IllegalArgumentException::class.java) {
            PersistedRealtimeCursor(-1, "generation")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PersistedRealtimeCursor(1, "")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PersistedRealtimeCursor(1, "g".repeat(257))
        }
    }

    @Test
    fun `catch-up page is sorted and requires a strict cursor advance`() {
        val later = event(12)
        val earlier = event(11)

        assertEquals(
            listOf(earlier, later),
            validateAndOrderRealtimeCatchUpPage(
                events = listOf(later, earlier),
                afterEpoch = 10,
            ),
        )
        assertNull(
            validateAndOrderRealtimeCatchUpPage(
                events = listOf(event(10)),
                afterEpoch = 10,
            ),
        )
        assertNull(
            validateAndOrderRealtimeCatchUpPage(
                events = listOf(event(11), event(11)),
                afterEpoch = 10,
            ),
        )
        assertNull(
            validateAndOrderRealtimeCatchUpPage(
                events = listOf(
                    buildJsonObject {
                        put("type", "event")
                    },
                ),
                afterEpoch = 10,
            ),
        )
    }

    private fun event(epoch: Int) = buildJsonObject {
        put("epoch_version", epoch)
        put("object_type", "message")
    }

    private companion object {
        const val ACCOUNT_A = "account-a"
        const val ACCOUNT_B = "account-b"
    }
}
