package ru.genesiscorporation.workspace.beta.data

import org.junit.Assert.*
import org.junit.Test
import ru.genesiscorporation.workspace.beta.data.remote.ProtectedRequestAuthentication

class VerifiedOwnerAuthenticationTest {
    @Test fun `verified owner request guard checks full configuration and exact credential before transmit`() {
        val captured = ServerConfig("server", "https://workspace.example", "image", "Workspace", false, "project")
        val authentication = ProtectedRequestAuthentication("synthetic-token")
        assertTrue(verifiedOwnerRequestStillCurrent(captured, captured.copy(), authentication, "synthetic-token"))
        for (current in listOf(captured.copy(id = "other"), captured.copy(baseUrl = "https://other.example"),
            captured.copy(projectUuid = "other"), captured.copy(needsToRelogin = true), captured.copy(imageUrl = "other"),
            captured.copy(name = "other"), null))
            assertFalse(verifiedOwnerRequestStillCurrent(captured, current, authentication, "synthetic-token"))
        assertFalse(verifiedOwnerRequestStillCurrent(captured, captured, authentication, "synthetic-replacement-token"))
    }
}
