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

package ru.genesiscorporation.workspace.beta.data.remote.dto

import org.junit.Assert.*
import org.junit.Test

class AddUsersToStreamRequestTest {
    @Test fun streamIdentifierCannotAlterTheRequestPath() {
        for (invalid in listOf("../users", "123/../../me", "%2e%2e%2fusers", "stream?project=other", "stream#fragment", " 00000000-0000-4000-8000-000000000000")) {
            assertThrows(IllegalArgumentException::class.java) { AddUsersToStreamRequest(invalid, listOf("member")) }
        }
    }

    @Test fun validIdentifierUsesTheFixedInvitationEndpoint() {
        val stream = "00000000-0000-4000-8000-000000000000"
        assertEquals("/api/workspace/v1/messenger/streams/$stream/actions/add_users/invoke",
            AddUsersToStreamRequest(stream, listOf("member")).url)
    }
}
