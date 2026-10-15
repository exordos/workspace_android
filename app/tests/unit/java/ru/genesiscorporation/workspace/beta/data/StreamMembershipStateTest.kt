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

package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData

class StreamMembershipStateTest {
    private val state = StreamMembershipState()
    private val owner = binding("owner", "owner")
    private val member = binding("member")
    private fun binding(id: String, role: String = "member", stream: String = "stream") =
        StreamBindingResponseData(id, role, stream, id)
    private fun snapshot(vararg bindings: StreamBindingResponseData) =
        state.beginRequest("stream").use { assertTrue(state.replaceSnapshot(it, bindings.toList())) }
    private fun current() = state.bindings.value.getValue("stream")

    @Test fun delayedInvitationCannotRestoreDeletedMemberInLoadedCache() {
        snapshot(owner, member)
        state.beginRequest("stream").use { request ->
            state.removeBinding("stream", member.uuid)
            state.mergeResponse(request, listOf(member))
        }
        assertEquals(listOf(owner), current())
    }

    @Test fun delayedSnapshotCannotRestoreDeletedMemberInLoadedCache() {
        snapshot(owner, member)
        state.beginRequest("stream").use { request ->
            state.removeBinding("stream", member.uuid)
            state.replaceSnapshot(request, listOf(owner, member))
        }
        assertEquals(listOf(owner), current())
    }

    @Test fun roleUpdateAndAdditionAfterRequestStartWinOverSnapshotAndInvitation() {
        snapshot(owner, member)
        val updated = member.copy(role = "administrator")
        val added = binding("new")
        state.beginRequest("stream").use { snapshot ->
            state.beginRequest("stream").use { invitation ->
                state.upsert("stream", listOf(updated, added))
                state.mergeResponse(invitation, listOf(member))
            }
            state.replaceSnapshot(snapshot, listOf(owner, member))
        }
        assertEquals(listOf(owner, updated, added), current())
    }

    @Test fun initialSnapshotReconcilesDeltasWithoutPublishingPartialMembership() {
        state.beginRequest("stream").use { request ->
            state.upsert("stream", listOf(owner))
            state.removeBinding("stream", member.uuid)
            assertNull(state.bindings.value["stream"])
            state.replaceSnapshot(request, listOf(member))
        }
        assertEquals(listOf(owner), current())
    }

    @Test fun eventsBeforeFirstRequestAreFoldedIntoInitialSnapshot() {
        val updated = owner.copy(role = "administrator")
        state.upsert("stream", listOf(updated))
        state.removeBinding("stream", member.uuid)
        assertNull(state.bindings.value["stream"])
        snapshot(owner, member)
        assertEquals(listOf(updated), current())
        // The first load consumes the journal; later snapshots are authoritative again.
        snapshot(owner, member)
        assertEquals(listOf(owner, member), current())
    }

    @Test fun overlappingFirstLoadsBothPreserveEventsThatPrecedeTheirRequests() {
        state.upsert("stream", listOf(member))
        state.beginRequest("stream").use { first ->
            state.beginRequest("stream").use { second ->
                state.replaceSnapshot(first, listOf(owner))
                state.replaceSnapshot(second, listOf(owner))
            }
        }
        assertEquals(listOf(owner, member), current())
    }

    @Test fun cancelledFirstLoadDoesNotDiscardEarlierEvents() {
        state.removeBinding("stream", member.uuid)
        state.beginRequest("stream").close()
        snapshot(owner, member)
        assertEquals(listOf(owner), current())
    }

    @Test fun invitationStartedAfterAnEarlierDeletionCanReaddTheMember() {
        state.removeBinding("stream", member.uuid)
        state.beginRequest("stream").use { assertTrue(state.mergeResponse(it, listOf(member))) }
        assertNull(state.bindings.value["stream"])
        snapshot(owner)
        assertEquals(listOf(owner, member), current())
    }

    @Test fun streamRemovalDiscardsEventsSavedBeforeItsFirstLoad() {
        state.upsert("stream", listOf(member))
        state.removeStream("stream")
        snapshot(owner)
        assertEquals(listOf(owner), current())
    }

    @Test fun confirmedInvitationDuringSnapshotLoadIsPreserved() {
        state.beginRequest("stream").use { snapshot ->
            state.beginRequest("stream").use { invitation ->
                state.mergeResponse(invitation, listOf(member))
            }
            assertNull(state.bindings.value["stream"])
            state.replaceSnapshot(snapshot, listOf(owner))
        }
        assertEquals(listOf(owner, member), current())
    }

    @Test fun nextSnapshotCanContainMemberReaddedAfterEarlierDeletion() {
        snapshot(owner, member)
        state.beginRequest("stream").use { request ->
            state.removeBinding("stream", member.uuid)
            state.replaceSnapshot(request, listOf(owner, member))
        }
        snapshot(owner, member)
        assertEquals(listOf(owner, member), current())
    }

    @Test fun streamRemovalInvalidatesBothPendingKindsOfHttpResults() {
        snapshot(owner)
        state.beginRequest("stream").use { snapshot ->
            state.beginRequest("stream").use { invitation ->
                state.removeStream("stream")
                assertFalse(state.replaceSnapshot(snapshot, listOf(owner, member)))
                assertFalse(state.mergeResponse(invitation, listOf(member)))
            }
        }
        assertNull(state.bindings.value["stream"])
    }

    @Test fun closedRequestsCannotPublishAndOtherStreamsDoNotChangeTheResult() {
        val cancelled = state.beginRequest("stream")
        cancelled.close()
        assertFalse(state.replaceSnapshot(cancelled, listOf(owner)))
        state.beginRequest("stream").use { request ->
            state.upsert("other", listOf(binding("owner", stream = "other")))
            state.removeBinding("other", owner.uuid)
            state.replaceSnapshot(request, listOf(owner))
        }
        assertEquals(listOf(owner), current())
    }
    @Test fun repositoryShutdownInvalidatesOutstandingRequests() {
        state.beginRequest("stream").use { request ->
            state.invalidateRequests()
            assertFalse(state.mergeResponse(request, listOf(member)))
        }
        assertTrue(state.bindings.value.isEmpty())
    }

    @Test fun deletionFollowedByReadditionWinsAndDuplicateBindingsStayUnique() {
        snapshot(owner, member, member)
        val replacement = member.copy(role = "administrator")
        state.beginRequest("stream").use { request ->
            state.removeBinding("stream", member.uuid)
            state.upsert("stream", listOf(replacement, replacement))
            state.mergeResponse(request, listOf(member, member))
        }
        assertEquals(listOf(owner, replacement), current())
        snapshot()
        assertTrue(current().isEmpty())
    }

    @Test fun responseForAnotherStreamCannotChangeTheRequestedStream() {
        snapshot(owner)
        state.beginRequest("stream").use { request ->
            assertFalse(state.replaceSnapshot(request, listOf(binding("foreign", stream = "other"))))
            assertFalse(state.mergeResponse(request, listOf(binding("foreign", stream = "other"))))
        }
        assertEquals(listOf(owner), current())
        assertNull(state.bindings.value["other"])
    }

}
