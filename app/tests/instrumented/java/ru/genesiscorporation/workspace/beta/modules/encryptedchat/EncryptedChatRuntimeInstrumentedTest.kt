package ru.genesiscorporation.workspace.beta.modules.encryptedchat

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.UserViewModel
import ru.genesiscorporation.workspace.beta.data.EventsRepository
import ru.genesiscorporation.workspace.beta.data.EventsRepositoryStore
import ru.genesiscorporation.workspace.beta.data.SecureTokenStore
import ru.genesiscorporation.workspace.beta.data.ServerConfig
import ru.genesiscorporation.workspace.beta.data.ServerRepository
import ru.genesiscorporation.workspace.beta.data.TokenPair
import ru.genesiscorporation.workspace.beta.data.crypto.ChatKeyAddress
import ru.genesiscorporation.workspace.beta.data.crypto.ChatKeyOwner
import ru.genesiscorporation.workspace.beta.data.crypto.ChatKeyScope
import ru.genesiscorporation.workspace.beta.data.crypto.EncryptedChatKeyStore
import ru.genesiscorporation.workspace.beta.data.crypto.PendingChatKeyRequest
import ru.genesiscorporation.workspace.beta.data.crypto.StagedChatCreation
import ru.genesiscorporation.workspace.beta.data.crypto.WorkspaceChatCrypto
import ru.genesiscorporation.workspace.beta.modules.profile.ProfileViewModel
import ru.genesiscorporation.workspace.beta.modules.login.LoginViewModel
import ru.genesiscorporation.workspace.beta.data.remote.WorkspaceAPIClient
import ru.genesiscorporation.workspace.beta.data.remote.dto.MessageResponse
import ru.genesiscorporation.workspace.beta.data.remote.dto.UserResponseData
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.chatdialog.ChatDialogViewModel
import ru.genesiscorporation.workspace.beta.modules.chatdialog.AttachmentStorage
import ru.genesiscorporation.workspace.beta.modules.createstream.CreateStreamViewModel
import java.io.File
import java.util.UUID
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.coroutines.CoroutineContext

/** Production runtime + authenticated HTTP + Android Keystore. Synthetic loopback accounts only. */
@RunWith(AndroidJUnit4::class)
class EncryptedChatRuntimeInstrumentedTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var backend: EncryptedConversationServer
    private lateinit var owner: Account
    private lateinit var member: Account
    private val keys by lazy { EncryptedChatKeyStore(context) }
    private val accounts = mutableListOf<Account>()

    @Before fun setup() = runBlocking {
        backend = EncryptedConversationServer()
        owner = account(backend.owner, "synthetic-access")
        member = account(backend.participant, "synthetic-member")
        assertTrue(keys.writeChatKey(owner.address(CURRENT), backend.groupKey, forSending = true))
    }

    @After fun cleanup() = runBlocking {
        accounts.forEach { account ->
            account.runtimes.forEach { it.close() }
            val streams = listOf(STREAM) + backend.createdStreams.map { it.uuid }
            streams.forEach { stream ->
                keys.listChatKeys(account.keyScope, stream).forEach { metadata ->
                    keys.removeChatKey(ChatKeyAddress(account.keyScope, stream, metadata.keyUuid))
                }
            }
            keys.listStagedCreations(account.keyScope).forEach { keys.discardStagedCreation(account.keyScope, it.creationId) }
            account.preferences.removeServer(account.config.id)
            account.events.clear()
            account.user.viewModelScope.cancel()
            account.scope.cancel()
            account.http.close()
            account.directory.deleteRecursively()
        }
        accounts.clear()
        if (::backend.isInitialized) backend.close()
    }

    @Test fun twoAuthenticatedRuntimesRequestGrantRejectRotateAndDeleteWithoutPlaintextCache() = runBlocking {
        val ownerRuntime = owner.runtime()
        val memberRuntime = member.runtime()
        ownerRuntime.readySession()
        memberRuntime.readySession()
        assertFalse(memberRuntime.state.value.hasCurrentKey)

        val firstRequest = memberRuntime.requestKey(CURRENT)
        owner.sync()
        ownerRuntime.await { it.requests.any { request -> request.message.uuid == firstRequest.uuid } }
        ownerRuntime.grantKey(firstRequest.uuid)
        val firstGrant = backend.messages.last { it.payload.kind == "key_grant" }
        member.sync()
        memberRuntime.await { it.hasCurrentKey }
        assertNotNull(keys.readChatKey(member.address(CURRENT)))

        val historicalText = "Encrypted reply **with Markdown** and [photo](urn:image:fixture-photo)"
        val historical = ownerRuntime.sendText(historicalText)
        member.sync()
        assertEquals(historicalText, (memberRuntime.presentation(historical) as EncryptedChatPresentation.Text).text)
        assertNotEquals(historicalText, historical.payload.content)
        assertTrue(member.repo.messagesPool.value.all { it.payload.kind != "markdown" })

        val editable = ownerRuntime.sendText("Before encrypted edit")
        val editedText = "Edited through the production JSON default policy"
        val edited = ownerRuntime.editText(editable.uuid, editedText)
        member.sync()
        assertEquals(editedText, (memberRuntime.presentation(edited) as EncryptedChatPresentation.Text).text)

        memberRuntime.removeLocalKey(CURRENT)
        assertFalse(memberRuntime.state.value.hasCurrentKey)
        val rejectedRequest = memberRuntime.requestKey(CURRENT)
        assertNotEquals(firstRequest.uuid, rejectedRequest.uuid)
        owner.sync()
        ownerRuntime.await { it.requests.any { request -> request.message.uuid == rejectedRequest.uuid } }
        ownerRuntime.rejectKey(rejectedRequest.uuid)
        member.sync()
        memberRuntime.await { it.requestStatus[CURRENT] == EncryptedChatRequestStatus.REJECTED }
        assertNull(keys.readChatKey(member.address(CURRENT)))

        val repeatedRequest = memberRuntime.requestKey(CURRENT)
        assertNotEquals(rejectedRequest.uuid, repeatedRequest.uuid)
        owner.sync()
        ownerRuntime.await { it.requests.any { request -> request.message.uuid == repeatedRequest.uuid } }
        ownerRuntime.grantKey(repeatedRequest.uuid)
        member.sync()
        memberRuntime.await { it.hasCurrentKey }
        assertEquals(historicalText, (memberRuntime.presentation(historical) as EncryptedChatPresentation.Text).text)

        val announcement = requireNotNull(ownerRuntime.rotate())
        val rotated = announcement.payload.keyUuid
        assertNotEquals(CURRENT, rotated)
        assertEquals(rotated, ownerRuntime.state.value.sendingKeyUuid)
        member.sync()
        memberRuntime.await { it.currentKeyUuid == rotated && !it.hasCurrentKey }
        assertEquals(CURRENT, memberRuntime.state.value.sendingKeyUuid)
        assertEquals(historicalText, (memberRuntime.presentation(historical) as EncryptedChatPresentation.Text).text)
        val current = ownerRuntime.sendText("New generation text")
        member.sync()
        assertTrue(memberRuntime.presentation(current) is EncryptedChatPresentation.Locked)
        val rotationRequest = memberRuntime.requestKey(rotated)
        owner.sync()
        ownerRuntime.await { it.requests.any { request -> request.message.uuid == rotationRequest.uuid } }
        ownerRuntime.grantKey(rotationRequest.uuid)
        val rotationGrant = backend.messages.last { it.payload.kind == "key_grant" }
        member.sync()
        memberRuntime.await { it.hasCurrentKey }
        assertEquals("New generation text", (memberRuntime.presentation(current) as EncryptedChatPresentation.Text).text)
        assertEquals(historicalText, (memberRuntime.presentation(historical) as EncryptedChatPresentation.Text).text)

        val secondPanel = member.runtime()
        secondPanel.readySession()
        secondPanel.presentation(current)
        secondPanel.removeLocalKey(rotated)
        withTimeout(10_000) { memberRuntime.presentations.first { it[current.uuid] !is EncryptedChatPresentation.Text } }
        assertTrue(memberRuntime.presentation(current) is EncryptedChatPresentation.Locked)
        assertNull(keys.readChatKey(member.address(rotated)))
        memberRuntime.readySession().ingest(listOf(firstGrant, rotationGrant).map { it.asEncryptedChatWire(PROJECT) })
        assertNull(keys.readChatKey(member.address(rotated)))
        assertTrue(memberRuntime.presentation(current) is EncryptedChatPresentation.Locked)
        assertEquals(historicalText, (memberRuntime.presentation(historical) as EncryptedChatPresentation.Text).text)
        try { memberRuntime.rotate(); fail("Only stream owner may rotate") }
        catch (expected: EncryptedChatException) { assertEquals(EncryptedChatFailure.FORBIDDEN, expected.failure) }
        assertFalse(backend.sentBodies.any { historicalText in it || "New generation text" in it })
        assertTrue(backend.rejectedPayloadKinds.isEmpty())
        assertTrue(backend.transportFailureTypes.isEmpty())
        assertEquals(setOf("e2ee", "key_request", "key_grant", "key_reject", "key_announced"),
            backend.sentPayloads.map { it.kind }.toSet())
        assertTrue(backend.editedPlaintexts.contains(editedText))
    }

    @Test fun replacementCredentialDeniesMountedAndNewRuntimeUntilMeThenSameOwnerRefreshRestores() = runBlocking {
        val mounted = owner.runtime()
        val oldSession = mounted.readySession()
        val message = currentEncryptedMessage()
        assertTrue(mounted.presentation(message) is EncryptedChatPresentation.Text)
        val identity = keys.readDeviceIdentity(owner.keyScope.owner)
        owner.preferences.updateServer(owner.config, TokenPair("synthetic-owner-refreshed", "synthetic-refresh"))
        withTimeout(5_000) { owner.user.accessToken.first { it == "synthetic-owner-refreshed" } }
        mounted.await { it.error == EncryptedChatFailure.STALE }
        assertTrue(mounted.presentations.value.isEmpty())
        assertFalse(oldSession.isCurrent())
        val newRuntime = owner.runtime()
        newRuntime.await { it.error == EncryptedChatFailure.STALE }
        val writes = backend.sentBodies.size
        for (runtime in listOf(mounted, newRuntime)) {
            try { runtime.sendText("must remain local"); fail("Unconfirmed replacement credential") }
            catch (expected: EncryptedChatException) { assertEquals(EncryptedChatFailure.STALE, expected.failure) }
        }
        assertEquals(writes, backend.sentBodies.size)
        assertEquals(identity, keys.readDeviceIdentity(owner.keyScope.owner))
        assertArrayEquals(backend.groupKey, keys.readChatKey(owner.address(CURRENT)))
        owner.repo.loadUserInfo() // Same User DTO; a new proof must still restore the runtime.
        mounted.await { it.hasCurrentKey }
        assertTrue(mounted.presentation(message) is EncryptedChatPresentation.Text)
        assertNotSame(oldSession, mounted.readySession())
        newRuntime.readySession()
        assertTrue(newRuntime.presentation(message) is EncryptedChatPresentation.Text)
    }

    @Test fun confirmedDifferentOwnerCannotRebindMountedRuntimeOrReadItsOldNamespace() = runBlocking {
        val mounted = owner.runtime()
        mounted.readySession()
        owner.preferences.updateServer(owner.config, TokenPair("synthetic-member", "synthetic-refresh"))
        withTimeout(5_000) { owner.user.accessToken.first { it == "synthetic-member" } }
        mounted.await { it.error == EncryptedChatFailure.STALE }
        owner.repo.loadUserInfo()
        assertEquals(backend.participant.uuid, owner.repo.currentUser.value?.uuid)
        try { mounted.presentation(backend.messages.first()); fail("Mounted owner changed") }
        catch (expected: EncryptedChatException) { assertEquals(EncryptedChatFailure.STALE, expected.failure) }
        assertTrue(mounted.presentations.value.isEmpty())
        assertArrayEquals(backend.groupKey, keys.readChatKey(owner.address(CURRENT)))
        assertNull(keys.readChatKey(member.address(CURRENT)))
    }

    @Test fun expiredAuthRetainsKeysButClearingFlagRequiresFreshMe() = runBlocking {
        val mounted = owner.runtime()
        mounted.readySession()
        owner.preferences.setNeedsToRelogin(true, owner.config.id)
        withTimeout(5_000) { owner.user.selectedServer.first { it?.needsToRelogin == true } }
        mounted.await { it.error == EncryptedChatFailure.STALE }
        assertArrayEquals(backend.groupKey, keys.readChatKey(owner.address(CURRENT)))
        owner.preferences.setNeedsToRelogin(false, owner.config.id)
        withTimeout(5_000) { owner.user.selectedServer.first { it?.needsToRelogin == false } }
        try { mounted.readySession(); fail("Clearing relogin flag is not proof") }
        catch (expected: EncryptedChatException) { assertEquals(EncryptedChatFailure.STALE, expected.failure) }
        owner.repo.loadUserInfo()
        mounted.await { it.hasCurrentKey }
        assertTrue(mounted.presentation(currentEncryptedMessage()) is EncryptedChatPresentation.Text)
    }

    @Test fun explicitProfileLogoutAfterExpiryPurgesOwnerSecretsAndClearsRuntimeWhilePreservingAnotherOwner() = runBlocking {
        val mounted = owner.runtime()
        mounted.readySession()
        assertTrue(mounted.presentation(currentEncryptedMessage()) is EncryptedChatPresentation.Text)
        val pending = owner.address("pending-generation")
        keys.writePendingRequest(pending, PendingChatKeyRequest("pending", null, WorkspaceChatCrypto().createRequestKeyPair(), 1))
        keys.writeStagedCreation(owner.keyScope, StagedChatCreation("staged", "staged-generation", backend.groupKey))
        keys.writeChatKey(member.address(CURRENT), backend.groupKey)
        owner.preferences.setNeedsToRelogin(true, owner.config.id)
        withTimeout(5_000) { owner.user.selectedServer.first { it?.needsToRelogin == true } }
        assertNull(owner.repo.verifiedOwnerAuthentication.value)
        val profile = withContext(Dispatchers.Main) { ProfileViewModel(owner.api, owner.user, owner.events) }
        try {
            withContext(Dispatchers.Main) { profile.logout() }
            mounted.await { it.error == EncryptedChatFailure.STALE }
            withTimeout(5_000) { owner.user.accessToken.first { it == null } }
            assertTrue(mounted.presentations.value.isEmpty())
            assertNull(keys.readDeviceIdentity(owner.keyScope.owner))
            assertNull(keys.readChatKey(owner.address(CURRENT)))
            assertTrue(keys.listPendingRequests(pending).isEmpty())
            assertTrue(keys.listStagedCreations(owner.keyScope).isEmpty())
            assertArrayEquals(backend.groupKey, keys.readChatKey(member.address(CURRENT)))
            assertFalse(keys.writeChatKey(owner.address(CURRENT), backend.groupKey)) // Delayed old writer.
        } finally { profile.viewModelScope.cancel() }
    }

    @Test fun unconfirmedReplacementCannotSelectAnotherOwnersNamespaceDuringExplicitRemoval() = runBlocking {
        keys.writeChatKey(member.address(CURRENT), backend.groupKey)
        val mounted = owner.runtime()
        mounted.readySession()
        owner.preferences.updateServer(owner.config, TokenPair("synthetic-member", "synthetic-refresh"))
        withTimeout(5_000) { owner.user.accessToken.first { it == "synthetic-member" } }
        mounted.await { it.error == EncryptedChatFailure.STALE }
        val profile = withContext(Dispatchers.Main) { ProfileViewModel(owner.api, owner.user, owner.events) }
        try {
            withContext(Dispatchers.Main) { profile.logout() }
            withTimeout(5_000) { owner.user.accessToken.first { it == null } }
            assertNull(keys.readChatKey(owner.address(CURRENT)))
            assertNull(keys.readDeviceIdentity(owner.keyScope.owner))
            assertArrayEquals(backend.groupKey, keys.readChatKey(member.address(CURRENT)))
            assertTrue(mounted.presentations.value.isEmpty())
        } finally { profile.viewModelScope.cancel() }
    }

    @Test fun reloginLeavePurgesOnlyConfirmedOrganizationAndPreservesSavedOrganizationAfterRestart() = runBlocking {
        for (restart in listOf(false, true)) {
            val account = account(backend.owner, "synthetic-access")
            val other = member.config.copy(id = "cassi-other-${UUID.randomUUID()}")
            keys.writeChatKey(account.address(CURRENT), backend.groupKey, forSending = true)
            val otherScope = ChatKeyScope(member.keyScope.owner, keys.ensureDeviceIdentity(member.keyScope.owner).deviceId)
            val otherAddress = ChatKeyAddress(otherScope, STREAM, CURRENT)
            assertTrue(keys.writeChatKey(otherAddress, backend.groupKey, forSending = true))
            val pending = account.address("pending-generation")
            keys.writePendingRequest(pending, PendingChatKeyRequest("pending", null, WorkspaceChatCrypto().createRequestKeyPair(), 1))
            keys.writeStagedCreation(account.keyScope, StagedChatCreation("staged", "staged-generation", backend.groupKey))
            account.preferences.addServer(other, TokenPair("synthetic-member", "synthetic-refresh"))
            account.preferences.setSelectedServerId(account.config.id)
            account.preferences.setNeedsToRelogin(true, account.config.id)
            var user = account.user
            var events = account.events
            var preferences = account.preferences
            var extraScope: CoroutineScope? = null
            if (restart) {
                // Fresh UserViewModel, repositories and encrypted preferences; no in-memory owner proof survives.
                account.events.clear()
                account.user.viewModelScope.cancel()
                events = EventsRepositoryStore(SecureTokenStore(context), account.api)
                extraScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                preferences = ServerRepository(account.dataStore, SecureTokenStore(context), events, extraScope)
                user = UserViewModel(preferences)
                account.api.attachUserViewModel(user)
                assertNull(events.getOrCreate(account.config).verifiedOwnerAuthentication.value)
            }
            try {
                withTimeout(5_000) { user.selectedServer.first { it?.id == account.config.id && it.needsToRelogin } }
                // Even replacement credentials cannot invent an owner; only the persisted /me association is erased.
                preferences.saveTokens(account.config.id, TokenPair("unconfirmed-replacement", "synthetic-refresh"))
                val login = LoginViewModel(account.api, user, false)
                assertTrue(login.leaveOrganization())
                withTimeout(5_000) { user.selectedServer.first { it?.id == other.id } }
                assertNull(preferences.getServer(account.config.id))
                assertNull(preferences.tokensFor(account.config.id))
                assertNull(keys.readDeviceIdentity(account.keyScope.owner))
                assertTrue(keys.listPendingRequests(pending).isEmpty())
                assertTrue(keys.listStagedCreations(account.keyScope).isEmpty())
                assertFalse(keys.writeChatKey(account.address(CURRENT), backend.groupKey))
                assertArrayEquals(backend.groupKey, keys.readChatKey(otherAddress))
                val remaining = events.getOrCreate(other)
                remaining.loadUserInfo()
                remaining.setInitialStreams(listOf(backend.stream.copy(userUuid = backend.participant.uuid)))
                remaining.addStreamBindings(STREAM, backend.bindings)
                remaining.addStreamTopicMessages(STREAM, TOPIC, backend.messages.map { it.copy(isOwn = it.authorUuid == backend.participant.uuid) })
                val runtime = EncryptedChatRuntime(context, account.api, remaining, account.scope, STREAM, TOPIC)
                try { assertTrue(runtime.presentation(currentEncryptedMessage()) is EncryptedChatPresentation.Text) }
                finally { runtime.close() }
                // A new owner identity can still be saved with the existing shared Keystore master key.
                assertNotEquals(account.keyScope.deviceId, keys.ensureDeviceIdentity(account.keyScope.owner).deviceId)
            } finally {
                preferences.removeServer(other.id)
                if (restart) { user.viewModelScope.cancel(); events.clear(); extraScope?.cancel() }
            }
        }
    }

    @Test fun backendStreamDeletionInvalidatesMountedPlaintextAndBlocksFurtherWrites() = runBlocking {
        val mounted = owner.runtime()
        val session = mounted.readySession()
        val message = currentEncryptedMessage()
        assertTrue(mounted.presentation(message) is EncryptedChatPresentation.Text)
        val posts = backend.sentBodies.size
        owner.repo.didReceiveStreamEvent("""{"kind":"stream.deleted","uuid":"$STREAM","source_name":"native","source":{"kind":"native"}}""", "deleted")
        mounted.await { it.error == EncryptedChatFailure.STALE }
        assertTrue(mounted.presentations.value.isEmpty())
        assertFalse(session.isCurrent())
        try { mounted.sendText("deleted chat"); fail("Deleted stream must be unavailable") }
        catch (expected: EncryptedChatException) { assertEquals(EncryptedChatFailure.STALE, expected.failure) }
        assertEquals(posts, backend.sentBodies.size)
    }

    @Test fun decryptResultsCannotPublishOrReturnAcrossRemovalBeforeObserversRun() = runBlocking {
        for (refresh in listOf(false, true)) {
            val observers = CoroutineScope(SupervisorJob() + QueuedDispatcher())
            val work = QueuedDispatcher()
            val worker = CoroutineScope(SupervisorJob() + work)
            val runtime = EncryptedChatRuntime(context, owner.api, owner.repo, observers, STREAM, TOPIC)
            try {
                owner.repo.setInitialStreams(listOf(backend.stream.copy(userUuid = owner.userUuid)))
                owner.sync()
                runtime.readySession()
                val result = worker.async {
                    runCatching { if (refresh) runtime.refresh(true) else runtime.presentation(currentEncryptedMessage()) }
                }
                work.next() // Dispatch ingest (refresh) or decrypt (presentation) to IO.
                if (refresh) repeat(backend.messages.size) { work.next() } // Final decrypt has finished on IO.
                val revision = owner.repo.streamRemovalRevision(STREAM)
                owner.repo.removeStream(STREAM)
                assertEquals(revision + 1, owner.repo.streamRemovalRevision(STREAM))
                // No observer has run. Reject even if an older projection is restored before resumption.
                owner.repo.setInitialStreams(listOf(backend.stream.copy(userUuid = owner.userUuid)))
                owner.sync()
                work.next()
                val error = withTimeout(5_000) { result.await() }.exceptionOrNull()
                assertTrue(error is EncryptedChatException && error.failure == EncryptedChatFailure.STALE)
                assertTrue(runtime.presentations.value.isEmpty())
            } finally { runtime.close(); observers.cancel(); worker.cancel() }
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val tasks = LinkedBlockingQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { tasks.add(block) }
        fun next() { checkNotNull(tasks.poll(5, TimeUnit.SECONDS)) { "Expected a suspended result" }.run() }
    }

    @Test fun ordinaryMountedChatRetainsComposerQuotesAndSelectionAcrossProofAndCredentialUpdates() = runBlocking {
        owner.repo.updateStream(backend.stream.copy(encryption = false, currentEncryptionKeyUuid = null, userUuid = OWNER))
        val ordinary = backend.messages.first().copy(payload = backend.messages.first().payload.copy(kind = "markdown", content = "ordinary message", keyUuid = null))
        owner.repo.addStreamTopicMessages(STREAM, TOPIC, listOf(ordinary))
        owner.repo.setInitialMessagesPool(listOf(ordinary))
        val chat = withContext(Dispatchers.Main) {
            ChatDialogViewModel(owner.api, owner.user, "Ordinary", STREAM, "General", TOPIC,
                false, owner.events, null, AttachmentStorage(context, owner.api))
        }
        try {
            delay(100) // Let initial draft loading finish before entering a new composer value.
            withContext(Dispatchers.Main) {
                chat.onAddQuoteMessageClicked(ordinary)
                chat.onMentionsChange(TextFieldValue("ordinary draft"))
                chat.startMessageSelection(ordinary)
            }
            val text = chat.mentions.text
            owner.preferences.updateServer(owner.config, TokenPair("synthetic-owner-refreshed", "synthetic-refresh"))
            withTimeout(5_000) { owner.user.accessToken.first { it == "synthetic-owner-refreshed" } }
            owner.repo.loadUserInfo()
            delay(100)
            withContext(Dispatchers.Main) {
                assertNull(chat.encryptedChatState.value.error)
                assertEquals(text, chat.mentions.text)
                assertEquals(listOf(ordinary.uuid), chat.quotedMessages.value.map { it.message.uuid })
                assertEquals(listOf(ordinary.uuid), chat.selectedMessages().map { it.uuid })
            }
        } finally {
            withContext(Dispatchers.Main) { chat.encryptionRuntime.close(); chat.viewModelScope.cancel() }
        }
    }

    @Test fun createEncryptedStreamFinalizesKeyAndInvitationRetryDoesNotCreateAnotherStream() = runBlocking {
        val viewModel = withContext(Dispatchers.Main) { CreateStreamViewModel(owner.api, owner.events, context) }
        try {
            withContext(Dispatchers.Main) {
                viewModel.setEncrypted(true)
                viewModel.onStreamNameChange("CASSI encrypted creation fixture")
                viewModel.didTapOnUser(backend.participant)
            }
            backend.failNextInvitation = true
            viewModel.createStream()
            assertTrue(viewModel.createQueryState.value is QueryState.Error)
            assertEquals(1, backend.streamCreationBodies.size)
            val created = requireNotNull(viewModel.createdStream)
            assertTrue(created.encryption)
            assertEquals(OWNER, created.ownerUuid)
            assertEquals(PROJECT, created.projectId)
            val keyAddress = ChatKeyAddress(owner.keyScope, created.uuid, requireNotNull(created.currentEncryptionKeyUuid))
            val stored = requireNotNull(keys.readChatKey(keyAddress))
            assertEquals(32, stored.size)
            assertTrue(keys.listStagedCreations(owner.keyScope).isEmpty())
            assertFalse(backend.streamCreationBodies.single().contains(ru.genesiscorporation.workspace.beta.data.crypto.WorkspaceChatCrypto.toBase64(stored)))
            viewModel.createStream()
            assertEquals(QueryState.Success, viewModel.createQueryState.value)
            assertEquals(1, backend.streamCreationBodies.size)
            assertEquals(2, backend.invitationAttempts)
            assertEquals(2, backend.invitationBodies.size)
            backend.invitationBodies.forEach { body ->
                val invitation = Json.parseToJsonElement(body).jsonObject
                assertEquals(setOf("member"), invitation.keys)
                assertEquals(listOf(backend.participant.uuid), invitation.getValue("member").jsonArray.map { it.jsonPrimitive.content })
            }
            assertEquals(created.uuid, backend.createdStreams.single().uuid)
            assertArrayEquals(stored, keys.readChatKey(keyAddress))
            assertTrue(owner.repo.streams.value.any { it.uuid == created.uuid && it.encryption })
            stored.fill(0)
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun ordinaryCreationSupportsLegacySessionWithoutKnownProjectOrKeyStoreContext() = runBlocking {
        // Start with the persisted legacy configuration this regression covers.
        // Updating an already selected project also tests unrelated flow transitions.
        val legacy = account(backend.owner, "synthetic-access", projectUuid = null)
        assertNull(legacy.user.selectedServer.value?.projectUuid)
        val stagedBefore = keys.listStagedCreations(legacy.keyScope).map { it.creationId }
        // No Context is supplied: ordinary creation must never try to initialize encrypted storage.
        val viewModel = withContext(Dispatchers.Main) { CreateStreamViewModel(legacy.api, legacy.events) }
        try {
            viewModel.onStreamNameChange("CASSI ordinary legacy fixture")
            viewModel.createStream()
            assertEquals(QueryState.Success, viewModel.createQueryState.value)
            assertEquals(1, backend.streamCreationBodies.size)
            val request = Json.parseToJsonElement(backend.streamCreationBodies.single()).jsonObject
            assertFalse(request["encryption"]?.jsonPrimitive?.contentOrNull == "true")
            assertNull(request["current_encryption_key_uuid"]?.jsonPrimitive?.contentOrNull)
            val created = requireNotNull(viewModel.createdStream)
            assertFalse(created.encryption)
            assertNull(created.currentEncryptionKeyUuid)
            assertTrue(legacy.repo.streams.value.any { it.uuid == created.uuid && !it.encryption })
            assertEquals(stagedBefore, keys.listStagedCreations(legacy.keyScope).map { it.creationId })
            assertTrue(keys.listChatKeys(legacy.keyScope, created.uuid).isEmpty())
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun definitivelyRejectedEncryptedCreationDiscardsKeyAndAllowsCorrectedAttempt() = runBlocking {
        val viewModel = withContext(Dispatchers.Main) { CreateStreamViewModel(owner.api, owner.events, context) }
        try {
            viewModel.setEncrypted(true)
            viewModel.onStreamNameChange("CASSI rejected creation")
            backend.nextCreationStatus = 400
            viewModel.createStream()
            assertTrue(viewModel.createQueryState.value is QueryState.Error)
            assertNull(viewModel.createdStream)
            assertTrue(keys.listStagedCreations(owner.keyScope).isEmpty())
            assertTrue(backend.createdStreams.isEmpty())
            val rejected = Json.parseToJsonElement(backend.streamCreationBodies.single()).jsonObject
            viewModel.setEncrypted(false)
            assertFalse(viewModel.encrypted.value)
            viewModel.setEncrypted(true)
            viewModel.onStreamNameChange("CASSI corrected creation")
            viewModel.createStream()
            assertEquals(QueryState.Success, viewModel.createQueryState.value)
            assertEquals(2, backend.streamCreationBodies.size)
            val corrected = Json.parseToJsonElement(backend.streamCreationBodies.last()).jsonObject
            assertEquals("CASSI corrected creation", corrected.getValue("name").jsonPrimitive.content)
            assertNotEquals(rejected.getValue("uuid"), corrected.getValue("uuid"))
            assertNotEquals(rejected.getValue("current_encryption_key_uuid"), corrected.getValue("current_encryption_key_uuid"))
            assertEquals(1, backend.createdStreams.size)
            assertTrue(keys.listStagedCreations(owner.keyScope).isEmpty())
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun uncertainEncryptedCreationRetainsExactRequestKeyAndCanonicalIdentity() = runBlocking {
        val viewModel = withContext(Dispatchers.Main) { CreateStreamViewModel(owner.api, owner.events, context) }
        try {
            viewModel.setEncrypted(true)
            viewModel.onStreamNameChange("CASSI uncertain creation")
            backend.dropNextCreationResponse = true
            viewModel.createStream()
            assertTrue(viewModel.createQueryState.value is QueryState.Error)
            assertNull(viewModel.createdStream)
            assertEquals(1, backend.createdStreams.size)
            val staged = keys.listStagedCreations(owner.keyScope).single()
            try {
                viewModel.onStreamNameChange("Must not replace the unresolved request")
                viewModel.setEncrypted(false)
                assertEquals("CASSI uncertain creation", viewModel.streamName.value)
                assertTrue(viewModel.encrypted.value)
                viewModel.createStream()
                assertEquals(QueryState.Success, viewModel.createQueryState.value)
                assertEquals(2, backend.streamCreationBodies.size)
                assertEquals(backend.streamCreationBodies.first(), backend.streamCreationBodies.last())
                val created = requireNotNull(viewModel.createdStream)
                assertEquals(staged.creationId, created.uuid)
                assertEquals(staged.keyUuid, created.currentEncryptionKeyUuid)
                assertEquals(1, backend.createdStreams.size)
                assertArrayEquals(staged.groupKey, keys.readChatKey(ChatKeyAddress(owner.keyScope, created.uuid, staged.keyUuid)))
                assertTrue(keys.listStagedCreations(owner.keyScope).isEmpty())
            } finally { staged.groupKey.fill(0) }
        } finally { viewModel.viewModelScope.cancel() }
    }

    @Test fun encryptedCreationWithoutKnownProjectFailsBeforePostOrStaging() = runBlocking {
        owner.preferences.updateServer(owner.config.copy(projectUuid = null))
        withTimeout(5_000) { owner.user.selectedServer.first { it != null && it.id == owner.config.id && it.projectUuid == null } }
        val stagedBefore = keys.listStagedCreations(owner.keyScope).map { it.creationId }
        val viewModel = withContext(Dispatchers.Main) { CreateStreamViewModel(owner.api, owner.events, context) }
        try {
            viewModel.setEncrypted(true)
            viewModel.onStreamNameChange("CASSI protected legacy fixture")
            viewModel.createStream()
            assertTrue(viewModel.createQueryState.value is QueryState.Error)
            assertEquals("Нет проекта", (viewModel.createQueryState.value as QueryState.Error).message)
            assertTrue(backend.streamCreationBodies.isEmpty())
            assertTrue(backend.createdStreams.isEmpty())
            assertNull(viewModel.createdStream)
            assertEquals(stagedBefore, keys.listStagedCreations(owner.keyScope).map { it.creationId })
        } finally { viewModel.viewModelScope.cancel() }
    }

    private fun currentEncryptedMessage(): MessageResponse = backend.messages.first {
        it.payload.kind == "e2ee" && it.payload.keyUuid == CURRENT
    }

    private suspend fun EncryptedChatRuntime.await(predicate: (EncryptedChatSessionState) -> Boolean) {
        withTimeout(10_000) { state.first(predicate) }
    }

    private suspend fun account(person: UserResponseData, token: String, projectUuid: String? = PROJECT): Account {
        val directory = File(context.cacheDir, "cassi-encrypted-runtime-${UUID.randomUUID()}").apply { mkdirs() }
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { File(directory, "fixture.preferences_pb") }
        val http = HttpClient(CIO) {
            install(HttpTimeout) { requestTimeoutMillis = 10_000; connectTimeoutMillis = 5_000 }
            // Match WorkspaceApplication's Ktor default policy. The peer validates
            // exact variant fields, so a default/null leak fails a real HTTP flow.
            install(ContentNegotiation) { json(Json { encodeDefaults = true; ignoreUnknownKeys = true }) }
        }
        val api = WorkspaceAPIClient(http)
        val tokens = SecureTokenStore(context)
        val events = EventsRepositoryStore(tokens, api)
        val preferences = ServerRepository(dataStore, tokens, events, scope)
        val config = ServerConfig("cassi-runtime-${UUID.randomUUID()}", backend.url, "", "CASSI test", projectUuid = projectUuid)
        preferences.addServer(config, TokenPair(token, "synthetic-refresh"))
        val user = UserViewModel(preferences)
        api.attachUserViewModel(user)
        try {
            withTimeout(5_000) {
                combine(user.selectedServerId, user.selectedServer, user.accessToken) { id, selected, access ->
                    id == config.id && selected != null && selected.id == config.id && selected.projectUuid == projectUuid && !access.isNullOrBlank()
                }.first { it }
            }
        } catch (timeout: TimeoutCancellationException) {
            throw AssertionError("Fixture authentication did not become ready: selectedIdMatches=${user.selectedServerId.value == config.id}; " +
                "selectedConfigMatches=${user.selectedServer.value?.id == config.id}; projectMatches=${user.selectedServer.value?.projectUuid == projectUuid}; " +
                "hasAccessToken=${!user.accessToken.value.isNullOrBlank()}; hasStoredAccessToken=${!preferences.tokensFor(config.id)?.accessToken.isNullOrBlank()}", timeout)
        }
        val identity = keys.ensureDeviceIdentity(ChatKeyOwner(backend.url, PROJECT, person.uuid), "CASSI runtime fixture")
        val keyScope = ChatKeyScope(ChatKeyOwner(backend.url, PROJECT, person.uuid), identity.deviceId)
        val repo = events.getOrCreate(config)
        repo.updateCurrentUser(person)
        repo.loadUserInfo()
        assertTrue(repo.verifiedOwnerAuthentication.value != null)
        repo.setInitialUsers(listOf(backend.owner, backend.participant))
        repo.setInitialStreams(listOf(backend.stream.copy(userUuid = person.uuid)))
        repo.addStreamBindings(STREAM, backend.bindings)
        repo.addStreamTopicMessages(STREAM, TOPIC, backend.messages.map { it.copy(isOwn = it.authorUuid == person.uuid) })
        return Account(config, scope, preferences, events, user, http, api, repo, directory, keyScope, person.uuid, dataStore).also { accounts += it }
    }

    private inner class Account(val config: ServerConfig, val scope: CoroutineScope, val preferences: ServerRepository,
        val events: EventsRepositoryStore, val user: UserViewModel, val http: HttpClient, val api: WorkspaceAPIClient,
        val repo: EventsRepository, val directory: File, val keyScope: ChatKeyScope, val userUuid: String,
        val dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>) {
        val runtimes = mutableListOf<EncryptedChatRuntime>()
        fun address(keyUuid: String) = ChatKeyAddress(keyScope, STREAM, keyUuid)
        fun runtime() = EncryptedChatRuntime(context, api, repo, scope, STREAM, TOPIC).also { runtimes += it }
        fun sync() {
            repo.updateStream(backend.stream.copy(userUuid = userUuid))
            repo.updateMessagesPool(backend.messages.map { it.copy(isOwn = it.authorUuid == userUuid) })
        }
    }

    companion object {
        private const val OWNER = EncryptedConversationUiTest.OWNER
        private const val STREAM = EncryptedConversationUiTest.STREAM
        private const val TOPIC = EncryptedConversationUiTest.TOPIC
        private const val CURRENT = EncryptedConversationUiTest.CURRENT
        private const val PROJECT = EncryptedConversationUiTest.PROJECT
    }
}
