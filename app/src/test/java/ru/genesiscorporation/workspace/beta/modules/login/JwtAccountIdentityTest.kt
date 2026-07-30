package ru.genesiscorporation.workspace.beta.modules.login

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.Base64

class JwtAccountIdentityTest {
    @Test
    fun readsCanonicalUuidSubject() {
        val token = jwt("""{"sub":"11111111-1111-4111-8111-111111111111"}""")

        assertEquals(
            "11111111-1111-4111-8111-111111111111",
            userUuidFromAccessToken(token),
        )
    }

    @Test
    fun rejectsMalformedAndNonUuidOwners() {
        assertNull(userUuidFromAccessToken("not-a-jwt"))
        assertNull(userUuidFromAccessToken(jwt("""{"sub":"nickname"}""")))
    }

    @Test
    fun readsOptionalProjectUuid() {
        val token = jwt(
            """{"sub":"11111111-1111-4111-8111-111111111111","project_id":"22222222-2222-4222-8222-222222222222"}""",
        )

        assertEquals(
            "22222222-2222-4222-8222-222222222222",
            projectUuidFromAccessToken(token),
        )
    }

    @Test
    fun validatesRefreshedTokenAgainstStoredAccountIdentity() {
        val user = "11111111-1111-4111-8111-111111111111"
        val project = "22222222-2222-4222-8222-222222222222"

        assertTrue(
            accessTokenMatchesAccount(
                jwt("""{"sub":"$user","project_id":"$project"}"""),
                expectedUserId = user,
                expectedProjectId = project,
            ),
        )
        assertTrue(
            accessTokenMatchesAccount(
                jwt("""{"sub":"$user"}"""),
                expectedUserId = user,
                expectedProjectId = project,
            ),
        )
        assertFalse(
            accessTokenMatchesAccount(
                jwt(
                    """{"sub":"33333333-3333-4333-8333-333333333333","project_id":"$project"}""",
                ),
                expectedUserId = user,
                expectedProjectId = project,
            ),
        )
        assertFalse(
            accessTokenMatchesAccount(
                jwt(
                    """{"sub":"$user","project_id":"44444444-4444-4444-8444-444444444444"}""",
                ),
                expectedUserId = user,
                expectedProjectId = project,
            ),
        )
    }

    private fun jwt(payload: String): String {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(
            payload.toByteArray(StandardCharsets.UTF_8),
        )
        return "header.$encoded.signature"
    }
}
