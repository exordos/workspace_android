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

import java.io.Closeable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.genesiscorporation.workspace.beta.data.remote.dto.StreamBindingResponseData

/** Reconciles HTTP results with changes received during each request's lifetime. */
internal class StreamMembershipState(private val lock: Any = Any()) {
    private val mutableBindings = MutableStateFlow<Map<String, List<StreamBindingResponseData>>>(emptyMap())
    val bindings = mutableBindings.asStateFlow()
    private val requests = mutableSetOf<Request>()
    // Keep only the latest change per binding until a complete first snapshot is published.
    private val unloadedChanges = mutableMapOf<String, MutableMap<String, StreamBindingResponseData?>>()

    inner class Request internal constructor(val streamUuid: String) : Closeable {
        // Each overlapping first load must retain its own copy when another load consumes the journal.
        internal val initialChanges = unloadedChanges[streamUuid].orEmpty().toMap()
        internal val changes = mutableMapOf<String, StreamBindingResponseData?>()
        override fun close() { synchronized(lock) { requests.remove(this) } }
    }

    fun beginRequest(streamUuid: String): Request = synchronized(lock) {
        Request(streamUuid).also { requests += it }
    }

    fun replaceSnapshot(request: Request, snapshot: List<StreamBindingResponseData>): Boolean = synchronized(lock) {
        if (request !in requests || snapshot.any { it.streamUuid != request.streamUuid }) return false
        val merged = snapshot.associateBy { it.uuid }.toMutableMap()
        (request.initialChanges + request.changes).forEach { (uuid, binding) ->
            if (binding == null) merged.remove(uuid) else merged[uuid] = binding
        }
        mutableBindings.value += request.streamUuid to merged.values.toList()
        unloadedChanges.remove(request.streamUuid)
        request.close()
        true
    }

    fun mergeResponse(request: Request, response: List<StreamBindingResponseData>): Boolean = synchronized(lock) {
        if (request !in requests || response.any { it.streamUuid != request.streamUuid }) return false
        // A later event wins even when the response contains the same binding UUID.
        val accepted = response.filterNot { it.uuid in request.changes }
        request.close()
        upsert(request.streamUuid, accepted)
        true
    }

    fun upsert(streamUuid: String, bindings: List<StreamBindingResponseData>) = synchronized(lock) {
        require(bindings.all { it.streamUuid == streamUuid })
        bindings.forEach { binding ->
            if (streamUuid !in mutableBindings.value) {
                unloadedChanges.getOrPut(streamUuid) { mutableMapOf() }[binding.uuid] = binding
            }
            requests.filter { it.streamUuid == streamUuid }.forEach { it.changes[binding.uuid] = binding }
        }
        mutableBindings.value[streamUuid]?.let { current ->
            val merged = current.associateBy { it.uuid } + bindings.associateBy { it.uuid }
            mutableBindings.value += streamUuid to merged.values.toList()
        }
        // An invitation/event alone does not establish a complete member list.
    }

    fun removeBinding(streamUuid: String, bindingUuid: String) = synchronized(lock) {
        if (streamUuid !in mutableBindings.value) {
            unloadedChanges.getOrPut(streamUuid) { mutableMapOf() }[bindingUuid] = null
        }
        requests.filter { it.streamUuid == streamUuid }.forEach { it.changes[bindingUuid] = null }
        mutableBindings.value[streamUuid]?.let { current ->
            mutableBindings.value += streamUuid to current.filterNot { it.uuid == bindingUuid }
        }
    }

    fun invalidateRequests() = synchronized(lock) {
        requests.clear()
        unloadedChanges.clear()
    }

    fun removeStream(streamUuid: String) = synchronized(lock) {
        requests.removeAll { it.streamUuid == streamUuid }
        unloadedChanges.remove(streamUuid)
        mutableBindings.value -= streamUuid
    }
}
