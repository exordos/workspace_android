package ru.genesiscorporation.workspace.beta.data

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ServerConfigCompatibilityTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test fun `configuration saved before relogin and project fields still loads`() {
        val saved = """[{"id":"legacy","baseUrl":"https://fixture.invalid","imageUrl":"","name":"Fixture"}]"""
        val config = json.decodeFromString<List<ServerConfig>>(saved).single()
        assertEquals("legacy", config.id)
        assertFalse(config.needsToRelogin)
        assertNull(config.projectUuid)
    }

    @Test fun `existing relogin and project selections are preserved`() {
        val saved = """[{"id":"current","baseUrl":"https://fixture.invalid","imageUrl":"","name":"Fixture","needsToRelogin":true,"projectUuid":"project"}]"""
        val config = json.decodeFromString<List<ServerConfig>>(saved).single()
        assertTrue(config.needsToRelogin)
        assertEquals("project", config.projectUuid)
    }
}
