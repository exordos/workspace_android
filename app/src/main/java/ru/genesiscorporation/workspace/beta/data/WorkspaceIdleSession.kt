package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import java.io.Closeable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class WorkspaceAuthIdleTimeout {
    SIX_HOURS,
    TWELVE_HOURS,
    ONE_DAY,
    THREE_DAYS,
    SEVEN_DAYS,
    NEVER,
}

fun WorkspaceAuthIdleTimeout.durationMillis(): Long? = when (this) {
    WorkspaceAuthIdleTimeout.SIX_HOURS -> 6L * HOUR_MILLIS
    WorkspaceAuthIdleTimeout.TWELVE_HOURS -> 12L * HOUR_MILLIS
    WorkspaceAuthIdleTimeout.ONE_DAY -> 24L * HOUR_MILLIS
    WorkspaceAuthIdleTimeout.THREE_DAYS -> 3L * DAY_MILLIS
    WorkspaceAuthIdleTimeout.SEVEN_DAYS -> 7L * DAY_MILLIS
    WorkspaceAuthIdleTimeout.NEVER -> null
}

internal data class WorkspaceIdleSessionEvaluation(
    val normalizedLastInteractionAtMillis: Long,
    val expiresAtMillis: Long?,
    val expired: Boolean,
)

internal fun evaluateWorkspaceIdleSession(
    persistedLastInteractionAtMillis: Long?,
    nowMillis: Long,
    timeoutMillis: Long?,
): WorkspaceIdleSessionEvaluation {
    require(nowMillis >= 0L) { "Current time must not be negative" }
    require(timeoutMillis == null || timeoutMillis > 0L) {
        "Idle timeout must be positive or disabled"
    }
    val normalizedLastInteraction = persistedLastInteractionAtMillis
        ?.takeIf { it in 0L..nowMillis }
        ?: nowMillis
    val expiresAt = timeoutMillis?.let { timeout ->
        if (Long.MAX_VALUE - normalizedLastInteraction < timeout) {
            Long.MAX_VALUE
        } else {
            normalizedLastInteraction + timeout
        }
    }
    return WorkspaceIdleSessionEvaluation(
        normalizedLastInteractionAtMillis = normalizedLastInteraction,
        expiresAtMillis = expiresAt,
        expired = expiresAt != null && nowMillis >= expiresAt,
    )
}

private val Context.workspaceIdleSessionDataStore by preferencesDataStore(
    name = "workspace_idle_session",
    corruptionHandler = ReplaceFileCorruptionHandler {
        emptyPreferences()
    },
)

internal interface WorkspaceIdleSessionPersistence {
    suspend fun readLastInteractionAtMillis(ownerKey: String): Long?

    suspend fun writeLastInteractionAtMillis(
        ownerKey: String,
        timestampMillis: Long,
    )

    suspend fun clear(ownerKey: String)
}

internal class WorkspaceIdleSessionStore(
    context: Context,
) : WorkspaceIdleSessionPersistence {
    private val dataStore = context.workspaceIdleSessionDataStore

    override suspend fun readLastInteractionAtMillis(ownerKey: String): Long? =
        dataStore.data.first()[lastInteractionKey(ownerKey)]

    override suspend fun writeLastInteractionAtMillis(
        ownerKey: String,
        timestampMillis: Long,
    ) {
        require(timestampMillis >= 0L) { "Interaction time must not be negative" }
        dataStore.edit { preferences ->
            preferences[lastInteractionKey(ownerKey)] = timestampMillis
        }
    }

    override suspend fun clear(ownerKey: String) {
        dataStore.edit { preferences ->
            preferences.remove(lastInteractionKey(ownerKey))
        }
    }

    private fun lastInteractionKey(ownerKey: String) = longPreferencesKey(
        "workspace_idle_last_interaction_v1_${workspaceStorageKey(ownerKey)}",
    )
}

/**
 * Owns the current account's foreground inactivity deadline. The last observed
 * interaction is persisted so killing the process cannot silently reset the
 * configured security boundary.
 */
internal class WorkspaceIdleSessionCoordinator(
    private val scope: CoroutineScope,
    private val store: WorkspaceIdleSessionPersistence,
    private val onSessionExpired: suspend (ownerKey: String) -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : Closeable {
    private val lock = Any()
    private var generation = 0L
    private var ownerKey: String? = null
    private var timeoutMillis: Long? = null
    private var lastInteractionAtMillis = 0L
    private var lastPersistedInteractionAtMillis = 0L
    private var foreground = false
    private var expirationInProgress = false
    private var expiryJob: Job? = null

    suspend fun activate(
        nextOwnerKey: String?,
        nextTimeoutMillis: Long?,
    ) {
        require(nextTimeoutMillis == null || nextTimeoutMillis > 0L) {
            "Idle timeout must be positive or disabled"
        }
        val now = nowMillis().coerceAtLeast(0L)
        var retainedEvaluation: WorkspaceIdleSessionEvaluation? = null
        val activationGeneration = synchronized(lock) {
            if (
                nextOwnerKey != null &&
                ownerKey == nextOwnerKey &&
                lastInteractionAtMillis > 0L
            ) {
                if (expirationInProgress) {
                    return
                }
                if (timeoutMillis == nextTimeoutMillis) {
                    scheduleExpiryLocked()
                    return
                }
                generation += 1L
                expiryJob?.cancel()
                expiryJob = null
                timeoutMillis = nextTimeoutMillis
                expirationInProgress = false
                val evaluation = evaluateWorkspaceIdleSession(
                    persistedLastInteractionAtMillis = lastInteractionAtMillis,
                    nowMillis = now,
                    timeoutMillis = nextTimeoutMillis,
                )
                retainedEvaluation = evaluation
                lastInteractionAtMillis = evaluation.normalizedLastInteractionAtMillis
                if (!evaluation.expired) {
                    scheduleExpiryLocked()
                }
                return@synchronized generation
            }
            generation += 1L
            expiryJob?.cancel()
            expiryJob = null
            ownerKey = nextOwnerKey
            timeoutMillis = nextTimeoutMillis
            expirationInProgress = false
            if (nextOwnerKey == null) {
                lastInteractionAtMillis = 0L
                lastPersistedInteractionAtMillis = 0L
            }
            generation
        }
        retainedEvaluation?.let { evaluation ->
            if (evaluation.expired) {
                expire(requireNotNull(nextOwnerKey))
            }
            return
        }
        if (nextOwnerKey == null) {
            return
        }

        val persisted = try {
            store.readLastInteractionAtMillis(nextOwnerKey)
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            null
        }
        val evaluation = evaluateWorkspaceIdleSession(
            persistedLastInteractionAtMillis = persisted,
            nowMillis = now,
            timeoutMillis = nextTimeoutMillis,
        )
        var shouldPersistNormalizedValue = false
        var shouldExpire = false
        synchronized(lock) {
            if (
                activationGeneration != generation ||
                ownerKey != nextOwnerKey ||
                timeoutMillis != nextTimeoutMillis
            ) {
                return
            }
            lastInteractionAtMillis = evaluation.normalizedLastInteractionAtMillis
            lastPersistedInteractionAtMillis = persisted ?: 0L
            shouldPersistNormalizedValue =
                persisted != evaluation.normalizedLastInteractionAtMillis
            shouldExpire = evaluation.expired
            if (!shouldExpire) {
                scheduleExpiryLocked()
            }
        }
        if (shouldPersistNormalizedValue) {
            persistSnapshot(nextOwnerKey, evaluation.normalizedLastInteractionAtMillis)
        }
        if (shouldExpire) {
            expire(nextOwnerKey)
        }
    }

    fun setForeground(value: Boolean) {
        var ownerToExpire: String? = null
        synchronized(lock) {
            foreground = value
            expiryJob?.cancel()
            expiryJob = null
            if (!value || expirationInProgress) return
            val owner = ownerKey ?: return
            val timeout = timeoutMillis ?: return
            val evaluation = evaluateWorkspaceIdleSession(
                persistedLastInteractionAtMillis = lastInteractionAtMillis,
                nowMillis = nowMillis().coerceAtLeast(0L),
                timeoutMillis = timeout,
            )
            if (evaluation.expired) {
                ownerToExpire = owner
            } else {
                scheduleExpiryLocked()
            }
        }
        ownerToExpire?.let(::expire)
    }

    fun recordInteraction() {
        val now = nowMillis().coerceAtLeast(0L)
        var snapshot: Pair<String, Long>? = null
        synchronized(lock) {
            val owner = ownerKey ?: return
            if (expirationInProgress) return
            lastInteractionAtMillis = now
            scheduleExpiryLocked()
            if (now - lastPersistedInteractionAtMillis >= PERSIST_INTERVAL_MILLIS) {
                lastPersistedInteractionAtMillis = now
                snapshot = owner to now
            }
        }
        snapshot?.let { (owner, timestamp) ->
            persistSnapshot(owner, timestamp)
        }
    }

    fun flush() {
        val snapshot = synchronized(lock) {
            val owner = ownerKey ?: return
            if (expirationInProgress) return
            if (lastInteractionAtMillis <= 0L) return
            lastPersistedInteractionAtMillis = lastInteractionAtMillis
            owner to lastInteractionAtMillis
        }
        persistSnapshot(snapshot.first, snapshot.second)
    }

    override fun close() {
        synchronized(lock) {
            generation += 1L
            ownerKey = null
            expiryJob?.cancel()
            expiryJob = null
            expirationInProgress = false
        }
    }

    private fun scheduleExpiryLocked() {
        expiryJob?.cancel()
        expiryJob = null
        if (!foreground || expirationInProgress) return
        val owner = ownerKey ?: return
        val timeout = timeoutMillis ?: return
        val now = nowMillis().coerceAtLeast(0L)
        val remaining = evaluateWorkspaceIdleSession(
            persistedLastInteractionAtMillis = lastInteractionAtMillis,
            nowMillis = now,
            timeoutMillis = timeout,
        ).expiresAtMillis
            ?.minus(now)
            ?.coerceAtLeast(0L)
            ?: return
        val expectedGeneration = generation
        expiryJob = scope.launch {
            delay(remaining)
            val stillCurrent = synchronized(lock) {
                expectedGeneration == generation &&
                    ownerKey == owner &&
                    !expirationInProgress
            }
            if (stillCurrent) {
                expire(owner)
            }
        }
    }

    private fun expire(expectedOwnerKey: String) {
        val expectedGeneration = synchronized(lock) {
            if (
                ownerKey != expectedOwnerKey ||
                expirationInProgress
            ) {
                return
            }
            expirationInProgress = true
            expiryJob?.cancel()
            expiryJob = null
            generation
        }
        scope.launch {
            val removed = try {
                onSessionExpired(expectedOwnerKey)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
                false
            }
            if (removed) {
                try {
                    store.clear(expectedOwnerKey)
                } catch (exception: Exception) {
                    if (exception is CancellationException) throw exception
                }
            }
            var retrySnapshot: Pair<String, Long>? = null
            synchronized(lock) {
                if (
                    expectedGeneration != generation ||
                    ownerKey != expectedOwnerKey
                ) {
                    return@synchronized
                }
                expirationInProgress = false
                if (!removed) {
                    val now = nowMillis().coerceAtLeast(0L)
                    lastInteractionAtMillis = now
                    lastPersistedInteractionAtMillis = now
                    retrySnapshot = expectedOwnerKey to now
                    scheduleExpiryLocked()
                }
            }
            retrySnapshot?.let { (owner, timestamp) ->
                persistSnapshot(owner, timestamp)
            }
        }
    }

    private fun persistSnapshot(owner: String, timestamp: Long) {
        scope.launch {
            try {
                store.writeLastInteractionAtMillis(owner, timestamp)
            } catch (exception: Exception) {
                if (exception is CancellationException) throw exception
            }
        }
    }

    private companion object {
        const val PERSIST_INTERVAL_MILLIS = 60L * 1_000L
    }
}

private const val HOUR_MILLIS = 60L * 60L * 1_000L
private const val DAY_MILLIS = 24L * HOUR_MILLIS
