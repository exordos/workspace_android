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

package ru.genesiscorporation.workspace.beta.modules.createstream

import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import ru.genesiscorporation.workspace.beta.fixtures.LoopbackWorkspaceServer
import ru.genesiscorporation.workspace.beta.fixtures.WorkspaceTestAccount
import ru.genesiscorporation.workspace.beta.data.*
import ru.genesiscorporation.workspace.beta.data.remote.dto.*
import ru.genesiscorporation.workspace.beta.modules.adduserstostream.*
import ru.genesiscorporation.workspace.beta.modules.chooseserver.QueryState
import ru.genesiscorporation.workspace.beta.modules.createdirectstream.*
import ru.genesiscorporation.workspace.beta.ui.theme.WokspaceTheme
import java.io.Closeable
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Production ViewModels and HTTP on Android, with three isolated authenticated accounts. */
@RunWith(AndroidJUnit4::class)
class StreamMembershipInstrumentedTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var peer: MembershipPeer
    private val accounts = mutableListOf<WorkspaceTestAccount>()
    private val models = mutableListOf<androidx.lifecycle.ViewModel>()

    @Before fun setup() { peer = MembershipPeer() }
    @After fun cleanup(): Unit = runBlocking {
        models.forEach { it.viewModelScope.cancel() }
        accounts.forEach { it.close() }
        peer.close()
    }

    @Test fun createsStreamForOneAndTwoOtherAccountsAndDecodesBindingArrays(): Unit = runBlocking {
        val owner = account(0)
        val second = account(1)
        val third = account(2)
        for (count in 1..2) {
            val model = create(owner)
            model.onStreamNameChange("CASSI $count peers")
            peer.people.drop(1).take(count).forEach(model::didTapOnUser)
            model.createStream()
            assertEquals(QueryState.Success, model.createQueryState.value)
            val stream = requireNotNull(model.createdStream)
            assertEquals((peer.people.take(count + 1).map { it.uuid }).toSet(), peer.members[stream.uuid])
            for (account in listOf(owner, second, third)) {
                val response = account.api.performRequest(StreamsRequest())
                assertTrue(response is ru.genesiscorporation.workspace.beta.data.remote.ApiResult.Success)
                val visible = (response as ru.genesiscorporation.workspace.beta.data.remote.ApiResult.Success).value
                assertEquals(account.index <= count, visible.any { it.uuid == stream.uuid })
            }
        }
        assertEquals(2, peer.creations.size)
    }

    @Test fun invitationFailureIsVisibleAndRetryKeepsOriginalMembersAndStream(): Unit = runBlocking {
        val owner = account(0)
        val model = create(owner)
        model.onStreamNameChange("CASSI retry")
        model.didTapOnUser(peer.people[1])
        peer.failInvitation = true
        model.createStream()
        assertTrue(model.createQueryState.value is QueryState.Error)
        val mounted = mutableStateOf(true)
        compose.setContent { if (mounted.value) WokspaceTheme { CreateStreamView(model, rememberNavController()) } }
        val error = (model.createQueryState.value as QueryState.Error).message
        compose.onNodeWithText(error).assertIsDisplayed()
        model.didTapOnUser(peer.people[1])
        model.didTapOnUser(peer.people[2])
        model.onStreamNameChange("Changed after partial success")
        assertEquals(listOf(peer.people[1].uuid), model.selectedUserUuids.value)
        assertEquals("CASSI retry", model.streamName.value)
        // Stop composing before success: this assertion concerns retry state, not navigation.
        compose.runOnIdle { mounted.value = false }
        compose.waitForIdle()
        model.createStream()
        assertEquals(QueryState.Success, model.createQueryState.value)
        assertEquals(1, peer.creations.size)
        assertEquals(2, peer.invitations.size)
        assertEquals(peer.invitations[0], peer.invitations[1])
    }

    @Test fun addUsersFiltersCurrentMembershipAndTracksChangesWithoutUserReload(): Unit = runBlocking {
        val owner = account(0)
        val model = add(owner)
        val observer = launch { model.users.collect() }
        try {
            withTimeout(5_000) { model.users.first { it.size == 2 } }
            owner.repo.addStreamBindings(peer.initial.uuid, listOf(peer.binding(1)))
            withTimeout(5_000) { model.users.first { it.map { person -> person.uuid } == listOf(peer.people[2].uuid) } }
            owner.repo.addStreamBindings(peer.initial.uuid, emptyList())
            withTimeout(5_000) { model.users.first { it.size == 2 } }
        } finally { observer.cancelAndJoin() }
        Unit
    }

    @Test fun concurrentInvitationIsSingleAndDisplaysFailureInsteadOfGenericError(): Unit = runBlocking {
        val owner = account(0)
        val model = add(owner)
        model.didTapOnUser(peer.people[1])
        val gate = CountDownLatch(1)
        val mounted = mutableStateOf(true)
        peer.invitationGate = gate
        peer.failInvitation = true
        try {
            coroutineScope {
                val first = async { model.addUsersToStream() }
                withTimeout(5_000) { while (peer.invitations.isEmpty()) delay(10) }
                model.addUsersToStream()
                model.didTapOnUser(peer.people[2])
                assertEquals(listOf(peer.people[1].uuid), model.selectedUserUuids.value)
                compose.setContent { if (mounted.value) WokspaceTheme { AddUsersToStreamView(model, rememberNavController()) } }
                compose.onNodeWithText("Добавить").assertIsNotEnabled()
                gate.countDown()
                first.await()
            }
            val error = (model.createQueryState.value as QueryState.Error).message
            assertTrue(error.isNotBlank())
            assertNotEquals("Error", error)
            compose.onNodeWithText(error).assertIsDisplayed()
            assertEquals(1, peer.invitations.size)
            compose.runOnIdle { mounted.value = false }
            compose.waitForIdle()
            model.addUsersToStream()
            assertEquals(QueryState.Success, model.createQueryState.value)
            assertEquals(2, peer.invitations.size)
        } finally { gate.countDown() }
    }

    @Test fun emptyInvitationAndSelfSelectionDoNotWrite(): Unit = runBlocking {
        val owner = account(0)
        val model = add(owner)
        model.addUsersToStream()
        model.didTapOnUser(peer.people[0])
        model.addUsersToStream()
        assertTrue(peer.invitations.isEmpty())
        val creation = create(owner)
        creation.didTapOnUser(peer.people[0])
        assertTrue(creation.selectedUserUuids.value.isEmpty())
    }

    @Test fun realtimeConfirmedInvitationFinishesRetryWithoutAnotherPost(): Unit = runBlocking {
        val owner = account(0)
        val model = add(owner)
        model.didTapOnUser(peer.people[1])
        peer.failInvitation = true
        model.addUsersToStream()
        assertTrue(model.createQueryState.value is QueryState.Error)
        owner.repo.addStreamBindings(peer.initial.uuid, listOf(peer.binding(0), peer.binding(1)))
        model.addUsersToStream()
        assertEquals(QueryState.Success, model.createQueryState.value)
        assertEquals(1, peer.invitations.size)
    }

    @Test fun invitationResponseImmediatelyUpdatesMembershipAndRepeatedSnapshotDoesNotDuplicateIt(): Unit = runBlocking {
        val owner = account(0)
        val model = add(owner)
        model.didTapOnUser(peer.people[1])
        model.addUsersToStream()
        assertEquals(QueryState.Success, model.createQueryState.value)
        val snapshot = owner.repo.streamBindings.value.getValue(peer.initial.uuid)
        assertEquals(peer.people.take(2).map { it.uuid }.toSet(), snapshot.map { it.userUuid }.toSet())
        owner.repo.mergeStreamBindings(peer.initial.uuid, snapshot)
        assertEquals(2, owner.repo.streamBindings.value.getValue(peer.initial.uuid).size)
    }

    @Test fun canonicalBindingEventsUpdatePickerRolesAndRemovalWithoutReloadingUsers(): Unit = runBlocking {
        val owner = account(0)
        val model = add(owner)
        val observer = launch { model.users.collect() }
        try {
            withTimeout(5_000) { model.users.first { it.size == 2 } }
            val added = peer.binding(1)
            val batch = buildJsonObject {
                put("kind", "stream_bindings.created")
                put("uuid", peer.initial.uuid)
                put("items", Json.encodeToJsonElement(listOf(added)))
            }.toString()
            repeat(2) { owner.repo.didReceiveStreamBindingEvent(batch, "created") }
            withTimeout(5_000) { model.users.first { it.map { user -> user.uuid } == listOf(peer.people[2].uuid) } }
            assertEquals(2, owner.repo.streamBindings.value.getValue(peer.initial.uuid).size)
            val own = owner.repo.streamBindings.value.getValue(peer.initial.uuid).first { it.userUuid == peer.people[0].uuid }
            owner.repo.didReceiveStreamBindingEvent(Json.encodeToString(own.copy(role = "administrator")), "updated")
            assertEquals("administrator", owner.repo.streams.value.single().role)
            owner.repo.didReceiveStreamBindingEvent(buildJsonObject {
                put("uuid", added.uuid); put("stream_uuid", added.streamUuid); put("user_uuid", added.userUuid)
            }.toString(), "deleted")
            withTimeout(5_000) { model.users.first { it.size == 2 } }
            owner.repo.didReceiveStreamBindingEvent(buildJsonObject {
                put("uuid", own.uuid); put("stream_uuid", own.streamUuid); put("user_uuid", own.userUuid)
            }.toString(), "deleted")
            assertTrue(owner.repo.streams.value.isEmpty())
        } finally { observer.cancelAndJoin() }
    }

    @Test fun creationContinuesWhenTheScreenIsDisposedDuringInvitation(): Unit = runBlocking {
        val owner = account(0)
        val model = create(owner)
        model.onStreamNameChange("CASSI recreation")
        model.didTapOnUser(peer.people[1])
        val mounted = mutableStateOf(true)
        val gate = CountDownLatch(1)
        peer.invitationGate = gate
        try {
            compose.setContent { if (mounted.value) WokspaceTheme { CreateStreamView(model, rememberNavController()) } }
            compose.onNodeWithText("Создать").performClick()
            withTimeout(5_000) { while (peer.invitations.isEmpty()) delay(10) }
            compose.runOnIdle { mounted.value = false }
            compose.waitForIdle()
            gate.countDown()
            withTimeout(5_000) { model.createQueryState.first { it is QueryState.Success || it is QueryState.Error } }
            assertEquals(QueryState.Success, model.createQueryState.value)
            assertEquals(1, peer.creations.size)
            assertEquals(1, peer.invitations.size)
        } finally { gate.countDown() }
    }

    @Test fun stalePickersCannotPostToTheNewlySelectedOrganization(): Unit = runBlocking {
        val owner = account(0)
        val invitation = add(owner)
        val direct = withContext(Dispatchers.Main) { CreateDirectStreamViewModel(owner.api, owner.events).also { models += it } }
        invitation.didTapOnUser(peer.people[1])
        val other = owner.config.copy(id = "cassi-other-${UUID.randomUUID()}")
        try {
            owner.preferences.addServer(other, TokenPair("cassi-fixture-1", "cassi-fixture-refresh"))
            owner.preferences.setSelectedServerId(other.id)
            withTimeout(5_000) { owner.user.selectedServer.first { it?.id == other.id } }
            invitation.addUsersToStream()
            direct.createPrivateStream(peer.people[1])
            assertTrue(invitation.createQueryState.value is QueryState.Error)
            assertTrue(direct.createQueryState.value is QueryState.Error)
            assertTrue(peer.invitations.isEmpty())
            assertTrue(peer.creations.isEmpty())
        } finally { owner.preferences.removeServer(other.id) }
    }

    @Test fun sameServerIdProjectSwitchRejectsAllStaleParticipantPickers(): Unit = runBlocking {
        val owner = account(0)
        val invitation = add(owner)
        val direct = withContext(Dispatchers.Main) { CreateDirectStreamViewModel(owner.api, owner.events).also { models += it } }
        val creation = create(owner)
        invitation.didTapOnUser(peer.people[1])
        creation.onStreamNameChange("CASSI stale project")
        creation.didTapOnUser(peer.people[1])
        owner.preferences.updateServerProjectUuid(owner.config.id, "other-project")
        withTimeout(5_000) { owner.user.selectedServer.first { it?.projectUuid == "other-project" } }
        invitation.addUsersToStream()
        direct.createPrivateStream(peer.people[1])
        creation.createStream()
        assertTrue(invitation.createQueryState.value is QueryState.Error)
        assertTrue(direct.createQueryState.value is QueryState.Error)
        assertTrue(creation.createQueryState.value is QueryState.Error)
        assertTrue(peer.invitations.isEmpty())
        assertTrue(peer.creations.isEmpty())
        val selected = requireNotNull(owner.user.selectedServer.value)
        val replacement = owner.events.getOrCreate(selected)
        assertNotSame(owner.repo, replacement)
        assertTrue(replacement.matchesServerConfiguration(selected))
        assertTrue(replacement.streamBindings.value.isEmpty())
    }

    @Test fun delayedInvitationHttpResponseCannotRestoreADeletedMember(): Unit = runBlocking {
        val owner = account(0)
        val model = add(owner)
        model.didTapOnUser(peer.people[1])
        val gate = CountDownLatch(1)
        peer.invitationGate = gate
        try {
            coroutineScope {
                val response = async { model.addUsersToStream() }
                withTimeout(5_000) { while (peer.people[1].uuid !in peer.members.getValue(peer.initial.uuid)) delay(10) }
                val binding = peer.binding(1)
                owner.repo.didReceiveStreamBindingEvent(buildJsonObject {
                    put("uuid", peer.initial.uuid)
                    put("items", Json.encodeToJsonElement(listOf(binding)))
                }.toString(), "created")
                owner.repo.didReceiveStreamBindingEvent(buildJsonObject {
                    put("uuid", binding.uuid); put("stream_uuid", binding.streamUuid); put("user_uuid", binding.userUuid)
                }.toString(), "deleted")
                gate.countDown()
                response.await()
            }
            assertEquals(QueryState.Success, model.createQueryState.value)
            assertEquals(listOf(peer.people[0].uuid), owner.repo.streamBindings.value.getValue(peer.initial.uuid).map { it.userUuid })
        } finally { gate.countDown() }
    }

    @Test fun creationActionStaysReachableInACompactWindow(): Unit = runBlocking {
        val model = create(account(0))
        model.onStreamNameChange("CASSI compact")
        compose.setContent {
            WokspaceTheme {
                Box(Modifier.height(220.dp)) { CreateStreamView(model, rememberNavController()) }
            }
        }
        compose.onNodeWithText("Создать").assertIsDisplayed().assertIsEnabled()
    }

    @Test fun directChatSearchMatchesFirstNameAndEmailWithNonmatchingLastName(): Unit = runBlocking {
        val owner = account(0)
        val model = withContext(Dispatchers.Main) { CreateDirectStreamViewModel(owner.api, owner.events).also { models += it } }
        compose.setContent { WokspaceTheme { CreateDirectStreamView(model, rememberNavController()) } }
        compose.waitUntil(5_000) { model.users.value.size == 2 }
        compose.onNodeWithText(peer.people[1].displayableName()).assertIsDisplayed()
        compose.onNodeWithText(peer.people[0].displayableName()).assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("Person1")
        compose.onNodeWithText(peer.people[1].displayableName()).assertIsDisplayed()
        compose.onNodeWithText(peer.people[2].displayableName()).assertDoesNotExist()
        compose.onAllNodes(hasSetTextAction())[0].performTextReplacement("person1@example.invalid")
        compose.onNodeWithText(peer.people[1].displayableName()).assertIsDisplayed()
    }

    private suspend fun create(a: WorkspaceTestAccount) = withContext(Dispatchers.Main) {
        CreateStreamViewModel(a.api, a.events).also { models += it }
    }
    private suspend fun add(a: WorkspaceTestAccount) = withContext(Dispatchers.Main) {
        AddUsersToStreamViewModel(peer.initial.uuid, a.api, a.events).also { models += it }
    }
    private suspend fun account(index: Int): WorkspaceTestAccount {
        val account = WorkspaceTestAccount.create(peer.url, index).also { accounts += it }
        account.repo.updateCurrentUser(peer.people[index])
        account.repo.setInitialUsers(peer.people)
        account.repo.setInitialStreams(listOf(peer.initial))
        account.repo.addStreamBindings(peer.initial.uuid, listOf(peer.binding(0)))
        return account
    }
}

private class MembershipPeer : Closeable {
    private val server = LoopbackWorkspaceServer(::handle)
    val url get() = server.url
    val people = (0..2).map { UserResponseData("person$it@example.invalid", "Person$it", "Surname$it", "person$it",
        "00000000-0000-4000-8000-00000000000$it", status = "active", avatar = "") }
    val initial = Stream(UUID.randomUUID().toString(), 0, 0, 0, "2026-10-16T00:00:00Z", "CASSI test", false, 0,
        defaultTopicUuid = UUID.randomUUID().toString(), notificationMode = "all_messages", userUuid = people[0].uuid)
    val members = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
    val creations = CopyOnWriteArrayList<Stream>()
    val invitations = CopyOnWriteArrayList<String>()
    @Volatile var failInvitation = false
    @Volatile var invitationGate: CountDownLatch? = null
    fun binding(index: Int, stream: String = initial.uuid) = StreamBindingResponseData(
        UUID.nameUUIDFromBytes("$stream.$index".toByteArray()).toString(),
        if (index == 0) "owner" else "member", stream, people[index].uuid)
    init { members[initial.uuid] = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply { add(people[0].uuid) } }

    private fun handle(http: LoopbackWorkspaceServer.Request): LoopbackWorkspaceServer.Response {
        val path = http.path
        val actor = http.headers["authorization"]?.substringAfterLast('-')?.toIntOrNull()?.let { people.getOrNull(it) }
        fun respond(value: String, status: Int = 200) = LoopbackWorkspaceServer.Response(value, status)
        if (path.endsWith("/get_token/invoke")) return respond("""{"access_token":"cassi-fixture-0","refresh_token":"cassi-fixture-refresh"}""")
        if (actor == null) return respond("{}", 401)
        return when {
            path.endsWith("/me/") -> respond(Json.encodeToString(actor))
            path.endsWith("/users/") -> respond(Json.encodeToString(people))
            path.endsWith("/streams/") && http.method == "POST" -> {
                val request = Json.parseToJsonElement(http.body).jsonObject
                val stream = initial.copy(uuid = request["uuid"]?.jsonPrimitive?.content ?: UUID.randomUUID().toString(),
                    name = request.getValue("name").jsonPrimitive.content, ownerUuid = actor.uuid, projectId = "project", userUuid = actor.uuid)
                creations += stream
                members[stream.uuid] = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply { add(actor.uuid) }
                respond(Json.encodeToString(stream), 201)
            }
            path.endsWith("/actions/add_users/invoke") -> {
                invitations += http.body
                val result = if (failInvitation) {
                    failInvitation = false
                    respond("{\"message\":\"Invitation unavailable\"}", 503)
                } else {
                    val stream = path.substringAfter("/streams/").substringBefore('/')
                    val request = Json.parseToJsonElement(http.body).jsonObject
                    check(request.keys == setOf("member"))
                    val added = request.getValue("member").jsonArray.map { it.jsonPrimitive.content }
                    members.getValue(stream).addAll(added)
                    respond(Json.encodeToString(added.map { binding(people.indexOfFirst { person -> person.uuid == it }, stream) }))
                }
                invitationGate?.await(5, TimeUnit.SECONDS)
                result
            }
            path.endsWith("/streams/") -> respond(Json.encodeToString((listOf(initial) + creations).filter { actor.uuid in members[it.uuid].orEmpty() }))
            else -> respond("[]")
        }
    }
    override fun close() { invitationGate?.countDown(); server.close() }
}
