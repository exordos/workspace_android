package ru.genesiscorporation.workspace.beta.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.io.File

private val Context.dataStore by preferencesDataStore(name = "settings")

data class ActiveCredentialSnapshot(
    val ownerKey: String?,
    val accountId: String?,
    val baseUrl: String?,
    val projectId: String?,
    val userId: String?,
    val accessToken: String?,
    val refreshToken: String?,
) {
    override fun toString(): String =
        "ActiveCredentialSnapshot(" +
            "ownerKeyPresent=${!ownerKey.isNullOrBlank()}, " +
            "accountIdPresent=${!accountId.isNullOrBlank()}, " +
            "baseUrlPresent=${!baseUrl.isNullOrBlank()}, " +
            "projectIdPresent=${!projectId.isNullOrBlank()}, " +
            "userIdPresent=${!userId.isNullOrBlank()}, " +
            "accessTokenPresent=${!accessToken.isNullOrBlank()}, " +
            "refreshTokenPresent=${!refreshToken.isNullOrBlank()})"
}

class ApiKeyRepository(
    private val context: Context,
    private val credentialStore: CredentialStore = TinkCredentialStore(context),
    private val clearAccountLocalData: suspend (String) -> Unit = {},
    private val clearAccountAttachmentCache: (String) -> Boolean = { ownerKey ->
        accountAttachmentCacheDirectory(context.cacheDir, ownerKey)
            .deleteRecursively()
    },
) {
    private val sessionMutationMutex = Mutex()

    companion object {
        private val BASE_URL = stringPreferencesKey("base_url")
        private val BASE_URLS = stringPreferencesKey("base_urls")
        private val ACCOUNTS = stringPreferencesKey("workspace_accounts_v2")
        private val ACTIVE_ACCOUNT_ID = stringPreferencesKey("active_workspace_account_id")
        private val RETURN_ACCOUNT_ID = stringPreferencesKey("return_workspace_account_id")
        private val PROJECT_ID_SUFFIX = "project_id"
        private val CREDENTIAL_REVISION = longPreferencesKey("credential_revision")
        private val json = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }
    }

    val accountsFlow: Flow<List<WorkspaceAccount>> = context.dataStore.data
        .map(::storedAccounts)

    val activeAccountIdFlow: Flow<String?> = context.dataStore.data
        .map { prefs ->
            prefs[ACTIVE_ACCOUNT_ID]
                ?.takeIf { accountId ->
                    storedAccounts(prefs).any { it.accountId == accountId }
                }
        }

    val activeAccountFlow: Flow<WorkspaceAccount?> = context.dataStore.data
        .map(::activeAccount)

    val baseUrlsFlow: Flow<List<String>> = context.dataStore.data
        .map { prefs ->
            val accountUrls = storedAccounts(prefs).map(WorkspaceAccount::baseUrl)
            val legacyUrls = prefs[BASE_URLS]
                ?.let { encoded ->
                    runCatching { json.decodeFromString<List<String>>(encoded) }.getOrNull()
                }
                .orEmpty()
            (accountUrls + legacyUrls).distinct()
        }

    val baseUrlFlow: Flow<String?> = context.dataStore.data
        .map { prefs -> activeAccount(prefs)?.baseUrl ?: prefs[BASE_URL] }

    val accessTokenFlow: Flow<String?> = context.dataStore.data
        .map { prefs ->
            credentialOwner(prefs)?.let { owner ->
                credentialStore.read(owner, Credential.ACCESS_TOKEN)
            }
        }

    val refreshTokenFlow: Flow<String?> = context.dataStore.data
        .map { prefs ->
            credentialOwner(prefs)?.let { owner ->
                credentialStore.read(owner, Credential.REFRESH_TOKEN)
            }
        }

    val projectIdFlow: Flow<String?> = context.dataStore.data
        .map { prefs ->
            activeAccount(prefs)?.projectId ?: prefs[BASE_URL]?.let { baseUrl ->
                prefs[stringPreferencesKey("${baseUrl}_$PROJECT_ID_SUFFIX")]
            }
        }

    val emailFlow: Flow<String?> = context.dataStore.data
        .map { prefs ->
            activeAccount(prefs)?.email ?: prefs[BASE_URL]?.let { baseUrl ->
                prefs[stringPreferencesKey("${baseUrl}_email")]
            }
        }

    val userIdFlow: Flow<String?> = context.dataStore.data
        .map { prefs ->
            activeAccount(prefs)?.userId ?: prefs[BASE_URL]?.let { baseUrl ->
                prefs[stringPreferencesKey("${baseUrl}_user_id")]
            }
        }

    suspend fun addBaseUrl(url: String) = sessionMutationMutex.withLock {
        context.dataStore.edit { prefs ->
            val current = prefs[BASE_URLS]
                ?.let { encoded ->
                    runCatching { json.decodeFromString<List<String>>(encoded) }.getOrNull()
                }
                .orEmpty()
            if (url !in current) {
                prefs[BASE_URLS] = json.encodeToString(current + url)
            }
            prefs[BASE_URL] = url
            prefs.remove(ACTIVE_ACCOUNT_ID)
            bumpRevision(prefs)
        }
    }

    suspend fun beginAddAccount() = sessionMutationMutex.withLock {
        context.dataStore.edit { prefs ->
            prefs[ACTIVE_ACCOUNT_ID]?.let { prefs[RETURN_ACCOUNT_ID] = it }
            prefs.remove(ACTIVE_ACCOUNT_ID)
            prefs.remove(BASE_URL)
            bumpRevision(prefs)
        }
    }

    suspend fun cancelPendingLogin() = sessionMutationMutex.withLock {
        cancelPendingLoginLocked()
    }

    private suspend fun cancelPendingLoginLocked() {
        context.dataStore.edit { prefs ->
            val accounts = storedAccounts(prefs)
            val returnAccountId = prefs[RETURN_ACCOUNT_ID]
            val next = accounts.firstOrNull { it.accountId == returnAccountId }
                ?: accounts.firstOrNull()
            if (next == null) {
                prefs.remove(ACTIVE_ACCOUNT_ID)
                prefs.remove(BASE_URL)
            } else {
                prefs[ACTIVE_ACCOUNT_ID] = next.accountId
                prefs[BASE_URL] = next.baseUrl
            }
            prefs.remove(RETURN_ACCOUNT_ID)
            bumpRevision(prefs)
        }
    }

    suspend fun activateAccount(accountId: String): Boolean =
        sessionMutationMutex.withLock {
            var activated = false
            context.dataStore.edit { prefs ->
                val account = storedAccounts(prefs)
                    .firstOrNull { it.accountId == accountId }
                    ?: return@edit
                prefs[ACTIVE_ACCOUNT_ID] = account.accountId
                prefs[BASE_URL] = account.baseUrl
                prefs.remove(RETURN_ACCOUNT_ID)
                bumpRevision(prefs)
                activated = true
            }
            activated
        }

    suspend fun removePendingBaseUrl() = sessionMutationMutex.withLock {
        removePendingBaseUrlLocked()
    }

    private suspend fun removePendingBaseUrlLocked() {
        context.dataStore.edit { prefs ->
            val pendingUrl = prefs[BASE_URL]
            if (pendingUrl != null) {
                val accountUrls = storedAccounts(prefs).map(WorkspaceAccount::baseUrl).toSet()
                val currentUrls = prefs[BASE_URLS]
                    ?.let { encoded ->
                        runCatching { json.decodeFromString<List<String>>(encoded) }.getOrNull()
                    }
                    .orEmpty()
                val updated = currentUrls.filterNot {
                    it == pendingUrl && it !in accountUrls
                }
                if (updated.isEmpty()) {
                    prefs.remove(BASE_URLS)
                } else {
                    prefs[BASE_URLS] = json.encodeToString(updated)
                }
            }
        }
        cancelPendingLoginLocked()
    }

    suspend fun activeCredentialSnapshot(): ActiveCredentialSnapshot =
        sessionMutationMutex.withLock {
            val prefs = context.dataStore.data.first()
            val account = activeAccount(prefs)
            val owner = credentialOwner(prefs)
            ActiveCredentialSnapshot(
                ownerKey = owner,
                accountId = account?.accountId,
                baseUrl = account?.baseUrl ?: prefs[BASE_URL],
                projectId = account?.projectId
                    ?: prefs[BASE_URL]?.let { baseUrl ->
                        prefs[stringPreferencesKey("${baseUrl}_$PROJECT_ID_SUFFIX")]
                    },
                userId = account?.userId
                    ?: prefs[BASE_URL]?.let { baseUrl ->
                        prefs[stringPreferencesKey("${baseUrl}_user_id")]
                    },
                accessToken = owner?.let {
                    credentialStore.read(it, Credential.ACCESS_TOKEN)
                },
                refreshToken = owner?.let {
                    credentialStore.read(it, Credential.REFRESH_TOKEN)
                },
            )
        }

    suspend fun isActiveCredentialOwner(expectedOwnerKey: String): Boolean =
        sessionMutationMutex.withLock {
            credentialOwner(context.dataStore.data.first()) == expectedOwnerKey
        }

    internal suspend fun <T> withActiveCredentialOwner(
        expectedOwnerKey: String,
        operation: suspend () -> T,
    ): T? = sessionMutationMutex.withLock {
        if (
            credentialOwner(context.dataStore.data.first()) != expectedOwnerKey
        ) {
            null
        } else {
            operation()
        }
    }

    suspend fun saveRefreshedTokensIfActive(
        expectedOwnerKey: String,
        accessToken: String,
        refreshToken: String?,
    ): Boolean = sessionMutationMutex.withLock {
        val prefs = context.dataStore.data.first()
        if (credentialOwner(prefs) != expectedOwnerKey) {
            return@withLock false
        }
        writeCredentialPairWithRollback(
            ownerKey = expectedOwnerKey,
            accessToken = accessToken,
            refreshToken = refreshToken,
        )
        notifyCredentialChange()
        true
    }

    suspend fun saveSessionCredentials(
        projectId: String,
        projectName: String,
        organizationName: String?,
        userId: String,
        login: String,
        accessToken: String,
        refreshToken: String,
    ) = sessionMutationMutex.withLock {
        val baseUrl = context.dataStore.data.first()[BASE_URL]
            ?: error("Workspace server is not selected")
        val account = WorkspaceAccount(
            accountId = buildWorkspaceAccountId(baseUrl, projectId, userId),
            baseUrl = baseUrl,
            projectId = projectId,
            projectName = projectName.trim().ifBlank { projectId },
            organizationName = organizationName?.trim()?.takeIf(String::isNotEmpty),
            userId = userId,
            login = login.trim(),
        )
        val previousAccessToken =
            credentialStore.read(account.accountId, Credential.ACCESS_TOKEN)
        val previousRefreshToken =
            credentialStore.read(account.accountId, Credential.REFRESH_TOKEN)
        try {
            writeCredentialPairWithRollback(
                ownerKey = account.accountId,
                accessToken = accessToken,
                refreshToken = refreshToken,
            )
            context.dataStore.edit { prefs ->
                val accounts = storedAccounts(prefs)
                val updated = if (accounts.any { it.accountId == account.accountId }) {
                    accounts.map { existing ->
                        if (existing.accountId == account.accountId) {
                            account.copy(
                                displayName = existing.displayName,
                                email = existing.email,
                                avatarUrn = existing.avatarUrn,
                            )
                        } else {
                            existing
                        }
                    }
                } else {
                    accounts + account
                }
                writeAccounts(prefs, updated)
                prefs[ACTIVE_ACCOUNT_ID] = account.accountId
                prefs[BASE_URL] = account.baseUrl
                prefs.remove(RETURN_ACCOUNT_ID)
                bumpRevision(prefs)
            }
        } catch (exception: Exception) {
            restoreCredential(
                account.accountId,
                Credential.ACCESS_TOKEN,
                previousAccessToken,
            )
            restoreCredential(
                account.accountId,
                Credential.REFRESH_TOKEN,
                previousRefreshToken,
            )
            throw exception
        }
    }

    suspend fun saveCurrentAccountProfile(
        userId: String,
        displayName: String,
        email: String?,
        avatarUrn: String?,
    ) {
        context.dataStore.edit { prefs ->
            val current = activeAccount(prefs) ?: return@edit
            if (current.userId != userId) return@edit
            writeAccounts(
                prefs,
                storedAccounts(prefs).map { account ->
                    if (account.accountId == current.accountId) {
                        account.withProfile(userId, displayName, email, avatarUrn)
                    } else {
                        account
                    }
                },
            )
        }
    }

    suspend fun removeActiveAccount(): Boolean =
        removeActiveAccountIfOwner(expectedOwnerKey = null)

    suspend fun removeActiveAccountIfOwner(expectedOwnerKey: String?): Boolean =
        sessionMutationMutex.withLock {
            val prefs = context.dataStore.data.first()
            val active = activeAccount(prefs)
            if (active == null) {
                val pendingOwner = credentialOwner(prefs)
                if (
                    expectedOwnerKey != null &&
                    pendingOwner != expectedOwnerKey
                ) {
                    return@withLock false
                }
                pendingOwner?.let { clearAccountLocalData(it) }
                removePendingBaseUrlLocked()
                return@withLock true
            }
            if (
                expectedOwnerKey != null &&
                active.accountId != expectedOwnerKey
            ) {
                return@withLock false
            }
            clearAccountLocalData(active.accountId)
            check(clearAccountAttachmentCache(active.accountId)) {
                "Could not clear the account attachment cache"
            }
            val previousAccessToken =
                credentialStore.read(active.accountId, Credential.ACCESS_TOKEN)
            val previousRefreshToken =
                credentialStore.read(active.accountId, Credential.REFRESH_TOKEN)
            try {
                credentialStore.remove(
                    active.accountId,
                    Credential.ACCESS_TOKEN,
                )
                credentialStore.remove(
                    active.accountId,
                    Credential.REFRESH_TOKEN,
                )
                context.dataStore.edit { mutablePrefs ->
                    val remaining = storedAccounts(mutablePrefs)
                        .filterNot { it.accountId == active.accountId }
                    writeAccounts(mutablePrefs, remaining)
                    val next = remaining.firstOrNull()
                    if (next == null) {
                        mutablePrefs.remove(ACTIVE_ACCOUNT_ID)
                        mutablePrefs.remove(BASE_URL)
                    } else {
                        mutablePrefs[ACTIVE_ACCOUNT_ID] = next.accountId
                        mutablePrefs[BASE_URL] = next.baseUrl
                    }
                    mutablePrefs.remove(RETURN_ACCOUNT_ID)
                    bumpRevision(mutablePrefs)
                }
            } catch (exception: Exception) {
                restoreCredential(
                    active.accountId,
                    Credential.ACCESS_TOKEN,
                    previousAccessToken,
                )
                restoreCredential(
                    active.accountId,
                    Credential.REFRESH_TOKEN,
                    previousRefreshToken,
                )
                throw exception
            }
            true
        }

    suspend fun clearAll() = sessionMutationMutex.withLock {
        val prefs = context.dataStore.data.first()
        (
            storedAccounts(prefs).map(WorkspaceAccount::accountId) +
                listOfNotNull(credentialOwner(prefs))
        )
            .distinct()
            .forEach { ownerKey -> clearAccountLocalData(ownerKey) }
        credentialStore.clear()
        context.dataStore.edit { prefs ->
            prefs.clear()
        }
        File(context.cacheDir, ATTACHMENT_CACHE_DIRECTORY).deleteRecursively()
    }

    suspend fun migrateLegacyCredentials() = sessionMutationMutex.withLock {
        removeLegacySharedAttachmentCache()
        var prefs = context.dataStore.data.first()
        val baseUrl = prefs[BASE_URL] ?: return
        val accessTokenKey = stringPreferencesKey("${baseUrl}_access_token")
        val refreshTokenKey = stringPreferencesKey("${baseUrl}_refresh_token")
        val accessToken = prefs[accessTokenKey]
        val refreshToken = prefs[refreshTokenKey]

        if (accessToken != null) {
            credentialStore.write(baseUrl, Credential.ACCESS_TOKEN, accessToken)
        }
        if (refreshToken != null) {
            credentialStore.write(baseUrl, Credential.REFRESH_TOKEN, refreshToken)
        }
        if (accessToken != null || refreshToken != null) {
            context.dataStore.edit { mutablePreferences ->
                mutablePreferences.remove(accessTokenKey)
                mutablePreferences.remove(refreshTokenKey)
            }
            prefs = context.dataStore.data.first()
        }

        if (storedAccounts(prefs).isNotEmpty()) return
        val projectId = prefs[stringPreferencesKey("${baseUrl}_$PROJECT_ID_SUFFIX")]
            ?: return
        val userId = prefs[stringPreferencesKey("${baseUrl}_user_id")]
            ?: return
        val accountId = buildWorkspaceAccountId(baseUrl, projectId, userId)
        val migratedAccessToken = credentialStore.read(baseUrl, Credential.ACCESS_TOKEN)
            ?: return
        val migratedRefreshToken = credentialStore.read(baseUrl, Credential.REFRESH_TOKEN)
        credentialStore.write(accountId, Credential.ACCESS_TOKEN, migratedAccessToken)
        if (migratedRefreshToken != null) {
            credentialStore.write(accountId, Credential.REFRESH_TOKEN, migratedRefreshToken)
        }
        val email = prefs[stringPreferencesKey("${baseUrl}_email")]
        val account = WorkspaceAccount(
            accountId = accountId,
            baseUrl = baseUrl,
            projectId = projectId,
            projectName = projectId,
            userId = userId,
            login = email ?: userId,
            email = email,
        )
        context.dataStore.edit { mutablePrefs ->
            writeAccounts(mutablePrefs, listOf(account))
            mutablePrefs[ACTIVE_ACCOUNT_ID] = account.accountId
            mutablePrefs[BASE_URL] = account.baseUrl
            bumpRevision(mutablePrefs)
        }
        credentialStore.remove(baseUrl, Credential.ACCESS_TOKEN)
        credentialStore.remove(baseUrl, Credential.REFRESH_TOKEN)
    }

    private suspend fun notifyCredentialChange() {
        context.dataStore.edit(::bumpRevision)
    }

    private fun writeCredentialPairWithRollback(
        ownerKey: String,
        accessToken: String,
        refreshToken: String?,
    ) {
        val previousAccessToken =
            credentialStore.read(ownerKey, Credential.ACCESS_TOKEN)
        val previousRefreshToken =
            credentialStore.read(ownerKey, Credential.REFRESH_TOKEN)
        try {
            credentialStore.write(
                ownerKey,
                Credential.ACCESS_TOKEN,
                accessToken,
            )
            if (!refreshToken.isNullOrBlank()) {
                credentialStore.write(
                    ownerKey,
                    Credential.REFRESH_TOKEN,
                    refreshToken,
                )
            }
        } catch (exception: Exception) {
            restoreCredential(
                ownerKey,
                Credential.ACCESS_TOKEN,
                previousAccessToken,
            )
            restoreCredential(
                ownerKey,
                Credential.REFRESH_TOKEN,
                previousRefreshToken,
            )
            throw exception
        }
    }

    private fun restoreCredential(
        ownerKey: String,
        credential: Credential,
        previousValue: String?,
    ) {
        runCatching {
            if (previousValue == null) {
                credentialStore.remove(ownerKey, credential)
            } else {
                credentialStore.write(ownerKey, credential, previousValue)
            }
        }
    }

    private fun removeLegacySharedAttachmentCache() {
        File(context.cacheDir, ATTACHMENT_CACHE_DIRECTORY)
            .listFiles()
            .orEmpty()
            .filter(File::isFile)
            .forEach(File::delete)
    }

    private fun storedAccounts(prefs: Preferences): List<WorkspaceAccount> =
        prefs[ACCOUNTS]
            ?.let { encoded ->
                runCatching {
                    json.decodeFromString<List<WorkspaceAccount>>(encoded)
                }.getOrNull()
            }
            .orEmpty()
            .distinctBy(WorkspaceAccount::accountId)

    private fun activeAccount(prefs: Preferences): WorkspaceAccount? {
        val activeId = prefs[ACTIVE_ACCOUNT_ID] ?: return null
        return storedAccounts(prefs).firstOrNull { it.accountId == activeId }
    }

    private fun credentialOwner(prefs: Preferences): String? =
        activeAccount(prefs)?.accountId ?: prefs[BASE_URL]

    private fun writeAccounts(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        accounts: List<WorkspaceAccount>,
    ) {
        if (accounts.isEmpty()) {
            prefs.remove(ACCOUNTS)
        } else {
            prefs[ACCOUNTS] = json.encodeToString(accounts)
        }
    }

    private fun bumpRevision(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
    ) {
        prefs[CREDENTIAL_REVISION] = (prefs[CREDENTIAL_REVISION] ?: 0L) + 1L
    }
}

fun accountAttachmentCacheDirectory(
    cacheDirectory: File,
    ownerKey: String,
): File = File(
    File(cacheDirectory, ATTACHMENT_CACHE_DIRECTORY),
    workspaceStorageKey(ownerKey),
)

fun accountAttachmentCacheSizeBytes(
    cacheDirectory: File,
    ownerKey: String,
): Long = accountAttachmentCacheDirectory(cacheDirectory, ownerKey)
    .listFiles()
    .orEmpty()
    .filter(File::isFile)
    .sumOf(File::length)

fun clearAccountAttachmentCache(
    cacheDirectory: File,
    ownerKey: String,
): Boolean {
    val directory = accountAttachmentCacheDirectory(cacheDirectory, ownerKey)
    return !directory.exists() || directory.deleteRecursively()
}

private const val ATTACHMENT_CACHE_DIRECTORY = "attachments"
