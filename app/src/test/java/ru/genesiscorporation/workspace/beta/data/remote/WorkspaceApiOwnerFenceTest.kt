package ru.genesiscorporation.workspace.beta.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceApiOwnerFenceTest {
    @Test
    fun `unscoped calls remain backward compatible`() {
        assertTrue(
            requestOwnerMatches(
                actualOwnerKey = "owner-b",
                expectedOwnerKey = null,
            ),
        )
    }

    @Test
    fun `owner scoped calls use only the exact credential snapshot`() {
        assertTrue(
            requestOwnerMatches(
                actualOwnerKey = "owner-a",
                expectedOwnerKey = "owner-a",
            ),
        )
        assertFalse(
            requestOwnerMatches(
                actualOwnerKey = "owner-b",
                expectedOwnerKey = "owner-a",
            ),
        )
        assertFalse(
            requestOwnerMatches(
                actualOwnerKey = null,
                expectedOwnerKey = "owner-a",
            ),
        )
    }
}
