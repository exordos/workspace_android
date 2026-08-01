package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

class WorkspaceIdleSessionTest {
    @Test
    fun `desktop parity presets map to exact durations`() {
        assertEquals(6L * HOUR_MILLIS, WorkspaceAuthIdleTimeout.SIX_HOURS.durationMillis())
        assertEquals(12L * HOUR_MILLIS, WorkspaceAuthIdleTimeout.TWELVE_HOURS.durationMillis())
        assertEquals(24L * HOUR_MILLIS, WorkspaceAuthIdleTimeout.ONE_DAY.durationMillis())
        assertEquals(3L * DAY_MILLIS, WorkspaceAuthIdleTimeout.THREE_DAYS.durationMillis())
        assertEquals(7L * DAY_MILLIS, WorkspaceAuthIdleTimeout.SEVEN_DAYS.durationMillis())
        assertNull(WorkspaceAuthIdleTimeout.NEVER.durationMillis())
    }

    @Test
    fun `missing or future timestamps start a fresh bounded session`() {
        val missing = evaluateWorkspaceIdleSession(
            persistedLastInteractionAtMillis = null,
            nowMillis = 10_000L,
            timeoutMillis = 5_000L,
        )
        val future = evaluateWorkspaceIdleSession(
            persistedLastInteractionAtMillis = 20_000L,
            nowMillis = 10_000L,
            timeoutMillis = 5_000L,
        )

        assertEquals(10_000L, missing.normalizedLastInteractionAtMillis)
        assertEquals(15_000L, missing.expiresAtMillis)
        assertFalse(missing.expired)
        assertEquals(missing, future)
    }

    @Test
    fun `persisted inactivity expires at the exact boundary`() {
        val before = evaluateWorkspaceIdleSession(
            persistedLastInteractionAtMillis = 1_000L,
            nowMillis = 5_999L,
            timeoutMillis = 5_000L,
        )
        val boundary = evaluateWorkspaceIdleSession(
            persistedLastInteractionAtMillis = 1_000L,
            nowMillis = 6_000L,
            timeoutMillis = 5_000L,
        )

        assertFalse(before.expired)
        assertTrue(boundary.expired)
        assertEquals(6_000L, boundary.expiresAtMillis)
    }

    @Test
    fun `never keeps the persisted activity without an expiry`() {
        val evaluation = evaluateWorkspaceIdleSession(
            persistedLastInteractionAtMillis = 1_000L,
            nowMillis = 9_000L,
            timeoutMillis = null,
        )

        assertEquals(1_000L, evaluation.normalizedLastInteractionAtMillis)
        assertNull(evaluation.expiresAtMillis)
        assertFalse(evaluation.expired)
    }

    @Test
    fun `expired persisted session removes and clears only its owner`() = runBlocking {
        val persistence = FakeIdleSessionPersistence(
            mutableMapOf("owner-a" to 1_000L, "owner-b" to 8_000L),
        )
        val expiredOwners = mutableListOf<String>()
        val coordinator = WorkspaceIdleSessionCoordinator(
            scope = this,
            store = persistence,
            onSessionExpired = { owner ->
                expiredOwners += owner
                true
            },
            nowMillis = { 10_000L },
        )

        coordinator.activate("owner-a", 5_000L)
        yield()

        assertEquals(listOf("owner-a"), expiredOwners)
        assertNull(persistence.values["owner-a"])
        assertEquals(8_000L, persistence.values["owner-b"])
        coordinator.close()
    }

    @Test
    fun `account switch cancels the previous owner's foreground deadline`() = runBlocking {
        val persistence = FakeIdleSessionPersistence()
        val expiredOwners = mutableListOf<String>()
        val coordinator = WorkspaceIdleSessionCoordinator(
            scope = this,
            store = persistence,
            onSessionExpired = { owner ->
                expiredOwners += owner
                true
            },
            nowMillis = { 10_000L },
        )

        coordinator.activate("owner-a", 1L)
        coordinator.setForeground(true)
        coordinator.activate("owner-b", null)
        delay(10L)

        assertTrue(expiredOwners.isEmpty())
        coordinator.close()
    }

    @Test
    fun `timeout change retains newer in-memory interaction checkpoint`() = runBlocking {
        var now = 4_000L
        val persistence = FakeIdleSessionPersistence(
            mutableMapOf("owner-a" to 1_000L),
        )
        val expiredOwners = mutableListOf<String>()
        val coordinator = WorkspaceIdleSessionCoordinator(
            scope = this,
            store = persistence,
            onSessionExpired = { owner ->
                expiredOwners += owner
                true
            },
            nowMillis = { now },
        )

        coordinator.activate("owner-a", 5_000L)
        now = 4_500L
        coordinator.recordInteraction()
        now = 7_000L
        coordinator.activate("owner-a", 3_000L)
        yield()

        assertTrue(expiredOwners.isEmpty())
        coordinator.close()
    }

    @Test
    fun `failed removal starts a fresh bounded retry window`() = runBlocking {
        val persistence = FakeIdleSessionPersistence(
            mutableMapOf("owner-a" to 1_000L),
        )
        val coordinator = WorkspaceIdleSessionCoordinator(
            scope = this,
            store = persistence,
            onSessionExpired = { false },
            nowMillis = { 10_000L },
        )

        coordinator.activate("owner-a", 5_000L)
        yield()
        yield()

        assertEquals(10_000L, persistence.values["owner-a"])
        coordinator.close()
    }

    private companion object {
        const val HOUR_MILLIS = 60L * 60L * 1_000L
        const val DAY_MILLIS = 24L * HOUR_MILLIS
    }
}

private class FakeIdleSessionPersistence(
    val values: MutableMap<String, Long> = mutableMapOf(),
) : WorkspaceIdleSessionPersistence {
    override suspend fun readLastInteractionAtMillis(ownerKey: String): Long? =
        values[ownerKey]

    override suspend fun writeLastInteractionAtMillis(
        ownerKey: String,
        timestampMillis: Long,
    ) {
        values[ownerKey] = timestampMillis
    }

    override suspend fun clear(ownerKey: String) {
        values.remove(ownerKey)
    }
}
