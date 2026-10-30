package com.exordos.workspace.data

import org.junit.Assert.*
import org.junit.Test
import com.exordos.workspace.data.remote.ProtectedRequestAuthentication
import java.util.UUID

class ConfirmedAccountContextTest {
    private val server = ServerConfig(UUID.randomUUID().toString(), "https://workspace.example", "icon", "Workspace", projectUuid = "project")
    private val lineage = Any()
    private fun proof(token: String = "synthetic-old", revision: Long = 1) = VerifiedOwnerAuthentication(
        server.id, server.baseUrl, "alice", ProtectedRequestAuthentication(token), revision, server.copy(), lineage)
    private fun confirmed(proof: VerifiedOwnerAuthentication?, token: String = "synthetic-old", selected: ServerConfig = server) =
        ConfirmedAccountContext.from(selected, selected.id, server.id, "alice", token, proof)

    @Test fun `new credential never combines with cached owner and normal confirmed refresh rebinds`() {
        val oldProof = proof()
        val old = requireNotNull(confirmed(oldProof))
        assertNull(confirmed(null))
        assertNull(confirmed(oldProof, "synthetic-bob"))
        val refreshed = requireNotNull(confirmed(proof("synthetic-refreshed", 2), "synthetic-refreshed"))
        assertTrue(old.sameOwner(refreshed))
        assertFalse(old.matches(refreshed))
        assertFalse(old.matches(confirmed(oldProof.copy(revision = 2))))
        assertNull(ConfirmedAccountContext.from(server, server.id, server.id, "bob", "synthetic-old", oldProof))
    }

    @Test fun `full server project selected connection credential and owner are required`() {
        val proof = proof()
        assertNull(confirmed(proof.copy(capturedServer = null)))
        for (changed in listOf(server.copy(projectUuid = "other"), server.copy(baseUrl = "https://other.example"),
            server.copy(name = "changed"), server.copy(imageUrl = "changed"), server.copy(needsToRelogin = true), server.copy(projectUuid = null))) {
            assertNull(confirmed(proof, selected = changed))
        }
        assertNull(ConfirmedAccountContext.from(server, "other", server.id, "alice", "synthetic-old", proof))
        assertNull(ConfirmedAccountContext.from(server, server.id, "other", "alice", "synthetic-old", proof))
    }

    @Test fun `logout blocks old repo including a late me but permits a fresh login with isolated lifetime`() {
        val proof = proof()
        val old = requireNotNull(confirmed(proof))
        old.revoke()
        assertNull(confirmed(proof))
        assertNull(confirmed(proof("synthetic-refreshed", 2), "synthetic-refreshed"))
        val relogin = requireNotNull(confirmed(proof("synthetic-new-login", 1).copy(lineage = Any()), "synthetic-new-login"))
        assertFalse(old.sameOwner(relogin))
        assertEquals(old.owner, relogin.owner)
    }

}
