package ru.genesiscorporation.workspace.beta.data.remote.dto

import org.junit.Assert.assertEquals
import org.junit.Test

class IamProjectsRequestTest {
    @Test
    fun `base login never contains a hardcoded project`() {
        val request = LoginRequest(
            username = "user",
            password = "secret",
            otp = "",
        )

        assertEquals("openid email profile", request.data.scope)
        assertEquals(
            "openid email profile project:project-uuid",
            workspaceProjectScope(" project-uuid "),
        )
    }

    @Test
    fun `projects request uses the temporary token only for IAM discovery`() {
        val request = IamProjectsRequest("temporary-token")

        assertEquals("/api/core/v1/iam/projects/", request.url)
        assertEquals(false, request.requiresApiKey)
        assertEquals(
            "Bearer temporary-token",
            request.additionalHeaders["Authorization"],
        )
    }

    @Test
    fun `project parser accepts direct RESTAlchemy collections`() {
        val projects = parseWorkspaceProjects(
            """
            [
              {
                "uuid": "project-1",
                "name": "Support",
                "description": "Customer conversations",
                "status": "active",
                "organization": {
                  "uuid": "organization-1",
                  "name": "Example"
                }
              }
            ]
            """.trimIndent(),
        )

        assertEquals(
            WorkspaceProject(
                uuid = "project-1",
                name = "Support",
                description = "Customer conversations",
                organizationName = "Example",
            ),
            projects.single(),
        )
    }

    @Test
    fun `project parser accepts gateway wrappers and skips malformed rows`() {
        val projects = parseWorkspaceProjects(
            """
            {
              "items": [
                {"uuid": "", "name": "Invalid", "status": "active"},
                {
                  "uuid": "project-2",
                  "name": "Engineering",
                  "description": null,
                  "status": "active",
                  "organization": null
                }
              ]
            }
            """.trimIndent(),
        )

        assertEquals(listOf("project-2"), projects.map(WorkspaceProject::uuid))
    }

    @Test
    fun `project parser collapses duplicate UUIDs but preserves equal names`() {
        val projects = parseWorkspaceProjects(
            """
            [
              {
                "uuid": "project-1",
                "name": "Workspace",
                "status": "active"
              },
              {
                "uuid": "project-1",
                "name": "Workspace",
                "status": "active"
              },
              {
                "uuid": "project-2",
                "name": "Workspace",
                "status": "active"
              }
            ]
            """.trimIndent(),
        )

        assertEquals(listOf("project-1", "project-2"), projects.map(WorkspaceProject::uuid))
    }

    @Test
    fun `project scoped refresh sends the selected scope`() {
        val request = TokenRefreshRequest(
            refreshToken = "temporary-refresh",
            scope = workspaceProjectScope("project-1"),
        )

        assertEquals(
            "openid email profile project:project-1",
            request.data.scope,
        )
    }
}
